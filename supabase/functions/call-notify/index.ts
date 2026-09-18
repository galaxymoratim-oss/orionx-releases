import { createSupabaseContext } from "npm:@supabase/server@1.4.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
});

function base64Url(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function pemBytes(pem: string): Uint8Array {
  const normalized = pem.replace(/\\n/g, "\n");
  const b64 = normalized.replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const raw = atob(b64);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

async function firebaseAccessToken(email: string, privateKey: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64Url(JSON.stringify({
    iss: email,
    sub: email,
    aud: "https://oauth2.googleapis.com/token",
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemBytes(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  ));
  const assertion = `${unsigned}.${base64Url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || !body?.access_token) {
    console.error("Firebase OAuth error", response.status, body);
    throw new Error("Firebase OAuth failed");
  }
  return body.access_token as string;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const { data: authContext, error: authError } = await createSupabaseContext(request, { auth: "user" });
    const callerId = authContext?.userClaims?.sub;
    if (authError || !authContext || !callerId) return json({ error: "Invalid or expired session" }, authError?.status ?? 401);

    const payload = await request.json().catch(() => ({}));
    const callId = String(payload?.call_id ?? "");
    if (!/^[0-9a-f-]{36}$/i.test(callId)) return json({ error: "Invalid call_id" }, 400);

    const firebaseProjectId = Deno.env.get("FIREBASE_PROJECT_ID");
    const firebaseClientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
    const firebasePrivateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");
    if (!firebaseProjectId || !firebaseClientEmail || !firebasePrivateKey) {
      return json({ error: "Firebase server credentials are not configured" }, 503);
    }

    const admin = authContext.supabaseAdmin;
    const { data: call, error: callError } = await admin
      .from("calls")
      .select("id,conversation_id,initiated_by,call_type,status")
      .eq("id", callId)
      .maybeSingle();
    if (callError) throw callError;
    if (!call || call.initiated_by !== callerId || call.status !== "ringing") return json({ error: "Call is not ringable" }, 409);

    const { data: participant, error: participantError } = await admin
      .from("call_participants")
      .select("user_id")
      .eq("call_id", callId)
      .neq("user_id", callerId)
      .limit(1)
      .maybeSingle();
    if (participantError) throw participantError;
    const peerId = participant?.user_id;
    if (!peerId) return json({ error: "Peer not found" }, 404);

    const { data: caller, error: callerError } = await admin
      .from("profiles")
      .select("display_name,nickname")
      .eq("id", callerId)
      .maybeSingle();
    if (callerError) throw callerError;
    const callerName = String(caller?.display_name || caller?.nickname || "OrionX");

    const { data: devices, error: devicesError } = await admin
      .from("user_devices")
      .select("push_token")
      .eq("user_id", peerId)
      .not("push_token", "is", null)
      .limit(12);
    if (devicesError) throw devicesError;
    const tokens = [...new Set((devices ?? []).map((d: { push_token?: string | null }) => d.push_token).filter(Boolean))] as string[];
    if (tokens.length === 0) return json({ sent: 0, reason: "no_push_tokens" });

    const accessToken = await firebaseAccessToken(firebaseClientEmail, firebasePrivateKey);
    const video = call.call_type === "video";
    const title = video ? "Входящий видеозвонок OrionX" : "Входящий звонок OrionX";
    const body = callerName;
    let sent = 0;

    for (const token of tokens) {
      const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(firebaseProjectId)}/messages:send`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json; charset=utf-8",
        },
        body: JSON.stringify({
          message: {
            token,
            data: {
              kind: video ? "video_call" : "audio_call",
              call_id: callId,
              conversation_id: String(call.conversation_id ?? ""),
              caller_id: callerId,
              title,
              body,
            },
            android: {
              priority: "high",
              ttl: "60s",
            },
          },
        }),
      });
      if (response.ok) sent++;
      else console.error("FCM send failed", response.status, await response.text());
    }

    return json({ sent, attempted: tokens.length });
  } catch (error) {
    console.error("call-notify error", error);
    return json({ error: "Internal server error" }, 500);
  }
});
