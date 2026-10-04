// The app shows `message` from error bodies (ErrorResponse in CommentsAPI.kt)
export function fail(status: number, message: string) {
  return Response.json({ message }, { status });
}

export function ok(message = "OK") {
  return Response.json({ message });
}

// The app posts application/x-www-form-urlencoded bodies (OkHttp FormBody)
export async function readForm(request: Request): Promise<Record<string, string>> {
  try {
    const form = await request.formData();
    const out: Record<string, string> = {};
    form.forEach((value, key) => {
      if (typeof value === "string") out[key] = value;
    });
    return out;
  } catch {
    return {};
  }
}

export function toInt(value: string | null | undefined): number | null {
  if (value == null || !/^-?\d+$/.test(value.trim())) return null;
  const n = Number(value);
  return Number.isSafeInteger(n) ? n : null;
}

export const MAX_COMMENT_LENGTH = 2000;

export function cleanContent(raw: string | undefined): string | null {
  const content = (raw ?? "").trim();
  if (!content || content.length > MAX_COMMENT_LENGTH) return null;
  return content;
}
