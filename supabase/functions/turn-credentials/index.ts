import { createSupabaseContext } from "npm:@supabase/server@1.4.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });

/**
 * Проверка сессии в два шага.
 *
 * Быстрый путь — локальная сверка подписи по JWKS проекта. Он давал два разных отказа:
 * «Supabase auth failed null» — подпись верна, но идентификатор читали как
 * `userClaims.sub`, которого в этом типе нет (см. ниже), — и `INVALID_CREDENTIALS`,
 * когда токен не принят по-настоящему. В обоих случаях клиент оставался без TURN,
 * и звонок умирал с «Ошибка ICE/TURN соединения».
 *
 * Запасной путь оставлен на случай второго отказа: спрашиваем сам Auth, он проверит
 * токен при любом алгоритме подписи. Безопасность не страдает — решение всё равно
 * принимает Supabase, а не мы.
 */
const authorizeUser = async (request: Request): Promise<string | Response> => {
  const { data: authContext, error: authError } =
    await createSupabaseContext(request, { auth: "user" });

  // `UserClaims` — нормализованное camelCase-представление, идентификатор в нём
  // называется `id`; сырое `sub` лежит отдельно, в `jwtClaims`. Прежний код читал
  // `userClaims.sub`, которого не существует, поэтому получал undefined при любом,
  // даже полностью валидном токене — в логах это «Supabase auth failed null».
  const context = authContext as Record<string, any> | null | undefined;
  const claimed = context?.userClaims?.id ?? context?.jwtClaims?.sub;
  if (!authError && typeof claimed === "string" && claimed.length > 0) return claimed;

  console.warn(
    "Local JWKS check did not yield a user id",
    authError?.code ?? "no-error",
    context ? Object.keys(context).join(",") : "no-context",
  );

  const bearer = request.headers.get("Authorization") ?? "";
  if (!bearer.toLowerCase().startsWith("bearer ")) {
    console.error("No bearer token in the request");
    return json({ error: "Invalid or expired session" }, 401);
  }

  const projectUrl = Deno.env.get("SUPABASE_URL");
  const apiKey = Deno.env.get("SUPABASE_ANON_KEY") ??
    Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? "";
  if (!projectUrl) {
    console.error("SUPABASE_URL is not available for the auth fallback");
    return json({ error: "Invalid or expired session" }, 401);
  }

  const probe = await fetch(`${projectUrl}/auth/v1/user`, {
    headers: { Authorization: bearer, apikey: apiKey },
  });
  if (!probe.ok) {
    console.error("Auth API rejected the token", probe.status, authError?.code);
    return json({ error: "Invalid or expired session" }, 401);
  }
  const user = await probe.json().catch(() => null);
  if (!user?.id) {
    console.error("Auth API returned no user id");
    return json({ error: "Invalid or expired session" }, 401);
  }
  console.log("JWKS check failed, accepted via Auth API", authError?.code);
  return user.id as string;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  try {
    const authorized = await authorizeUser(request);
    if (authorized instanceof Response) return authorized;

    const ident = Deno.env.get("XIRSYS_IDENT");
    const secret = Deno.env.get("XIRSYS_SECRET");
    const channel = Deno.env.get("XIRSYS_CHANNEL");

    if (!ident || !secret || !channel) {
      return json({ error: "Xirsys secrets are not configured" }, 500);
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);

    let upstream: Response;
    try {
      upstream = await fetch(
        `https://global.xirsys.net/_turn/${encodeURIComponent(channel)}`,
        {
          method: "PUT",
          headers: {
            Authorization: `Basic ${btoa(`${ident}:${secret}`)}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ format: "urls" }),
          signal: controller.signal,
        },
      );
    } finally {
      clearTimeout(timeout);
    }

    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      console.error("Xirsys error", upstream.status, body);
      return json(
        {
          error: "TURN provider request failed",
          providerStatus: upstream.status,
        },
        502,
      );
    }

    const value = body?.v ?? body;
    const rawServers = Array.isArray(value?.iceServers)
      ? value.iceServers
      : [];

    const iceServers = rawServers
      .map((server: Record<string, unknown>) => ({
        urls: server.urls ?? server.url,
        username: server.username,
        credential: server.credential,
      }))
      .filter((server: Record<string, unknown>) => server.urls);

    if (iceServers.length === 0) {
      console.error("Unexpected Xirsys response", body);
      return json({ error: "TURN provider returned no ICE servers" }, 502);
    }

    return json({
      iceServers,
      issuedAt: Math.floor(Date.now() / 1000),
      refreshAfter: Math.floor(Date.now() / 1000) + 3000,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      return json({ error: "TURN provider timeout" }, 504);
    }
    console.error("Unhandled function error", error);
    return json({ error: "Internal server error" }, 500);
  }
});
