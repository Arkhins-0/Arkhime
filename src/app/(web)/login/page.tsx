"use client";

import { useEffect, useSyncExternalStore } from "react";
import LoginScreen from "@/web/components/LoginScreen";
import { WEB_TOKEN_KEY } from "@/web/lib/config";

const noop = () => () => {};
function signedIn(): boolean {
  try {
    return !!localStorage.getItem(WEB_TOKEN_KEY);
  } catch {
    return false;
  }
}

// Already signed in on this browser: straight to the web app.
export default function LoginPage() {
  // null on the server, so nothing flashes before the browser is checked
  const hasToken = useSyncExternalStore(noop, signedIn, () => null);

  useEffect(() => {
    if (hasToken) window.location.replace("/app");
  }, [hasToken]);

  if (hasToken !== false) return null;
  return <LoginScreen />;
}
