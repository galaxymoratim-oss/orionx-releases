const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-notify-token",
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

const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";

async function adminSelect(select: string, table: string, filters: string[]): Promise<any[]> {
  if (!SUPABASE_URL || !SERVICE_KEY) throw new Error("Service key is not available");
  const url = `${SUPABASE_URL}/rest/v1/${table}?select=${encodeURIComponent(select)}&${filters.join("&")}`;
  const response = await fetch(url, {
    headers: {
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
    },
  });
  if (!response.ok) throw new Error(`REST ${table} ${response.status}: ${await response.text()}`);
  return response.json();
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  // Вызов идёт из триггера базы через pg_net, пользовательской сессии нет —
  // проверяем отдельный общий секрет вместо JWT.
  const notifyToken = Deno.env.get("NOTIFY_TOKEN") ?? "";
  if (!notifyToken || request.headers.get("x-notify-token") !== notifyToken) {
    return json({ error: "Unauthorized" }, 401);
  }

  try {
    const firebaseProjectId = Deno.env.get("FIREBASE_PROJECT_ID");
    const firebaseClientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
    const firebasePrivateKey = Deno.env.get("FIREBASE_PRIVATE_KEY");
    if (!firebaseProjectId || !firebaseClientEmail || !firebasePrivateKey) {
      return json({ error: "Firebase server credentials are not configured" }, 503);
    }

    const payload = await request.json().catch(() => ({}));
    const messageId = String(payload?.message_id ?? "");
    if (!/^[0-9a-f-]{36}$/i.test(messageId)) return json({ error: "Invalid message_id" }, 400);

    const [message] = await adminSelect(
      "id,conversation_id,sender_id,body,attachment_type,attachment_name,deleted_for_all",
      "messages",
      [`id=eq.${messageId}`, "limit=1"],
    );
    if (!message || message.deleted_for_all) return json({ sent: 0, reason: "message_unavailable" });

    const [conversation] = await adminSelect(
      "id,title,type",
      "conversations",
      [`id=eq.${message.conversation_id}`, "limit=1"],
    );
    if (!conversation) return json({ sent: 0, reason: "no_conversation" });

    const [sender] = await adminSelect(
      "display_name,nickname",
      "profiles",
      [`id=eq.${message.sender_id}`, "limit=1"],
    );
    const senderName = String(sender?.display_name || sender?.nickname || "OrionX");

    const members = await adminSelect(
      "user_id",
      "conversation_members",
      [
        `conversation_id=eq.${message.conversation_id}`,
        "left_at=is.null",
        "user_id=neq." + message.sender_id,
        "notifications_enabled=neq.false",
      ],
    );
    if (members.length === 0) return json({ sent: 0, reason: "no_members" });

    const accessToken = await firebaseAccessToken(firebaseClientEmail, firebasePrivateKey);
    const kind = conversation.type === "group" ? "group" : conversation.type === "channel" ? "channel" : "message";
    const title = conversation.type === "direct" ? senderName : String(conversation.title || senderName);
    const attachment = String(message.attachment_type ?? "");
    const bodyText = String(message.body ?? "").trim() ||
      (attachment.startsWith("image/") ? "📷 Фото"
        : attachment.startsWith("audio/") ? "🎙 Голосовое сообщение"
        : attachment.startsWith("video/") ? "🎬 Видео"
        : attachment ? "📎 Файл" : "");
    if (!bodyText) return json({ sent: 0, reason: "empty_body" });

    let sent = 0;
    for (const member of members) {
      const devices = await adminSelect(
        "push_token",
        "user_devices",
        [`user_id=eq.${member.user_id}`, "push_token=is.not_null", "limit=8"],
      );
      const tokens = [...new Set((devices ?? []).map((d: any) => String(d.push_token ?? "")).filter(Boolean))];
      for (const token of tokens) {
        const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(firebaseProjectId)}/messages:send`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              data: {
                kind,
                conversation_id: String(message.conversation_id),
                message_id: messageId,
                title,
                body: bodyText,
              },
              android: { priority: "HIGH" },
            },
          }),
        });
        if (response.ok) sent++;
        else console.error("FCM error", response.status, await response.text());
      }
    }
    return json({ sent });
  } catch (error) {
    console.error("message-notify error", error);
    return json({ error: String(error) }, 500);
  }
});
