/**
 * PKCE (Proof Key for Code Exchange) utilities
 * 
 * PKCE расширение OAuth 2.0 Authorization Code Grant
 * Добавляет два параметра:
 * - code_verifier: случайная строка 43-128 символов
 * - code_challenge: base64url(sha256(code_verifier))
 */

/**
 * Генерация случайной строки для code_verifier
 * @param length Длина строки (по умолчанию 43-128 символов)
 */
export function generateCodeVerifier(length: number = 128): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  let result = '';
  const array = new Uint8Array(length);
  window.crypto.getRandomValues(array);
  
  for (let i = 0; i < length; i++) {
    result += chars[array[i] % chars.length];
  }
  
  return result;
}

/**
 * Создание code_challenge из code_verifier методом S256
 * @param verifier code_verifier строка
 */
export async function createCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await window.crypto.subtle.digest('SHA-256', data);
  
  return base64urlencode(new Uint8Array(digest));
}

/**
 * Base64url encoding (RFC 4648)
 * @param buffer ArrayBuffer или Uint8Array
 */
function base64urlencode(buffer: ArrayBuffer | Uint8Array): string {
  let binary = '';
  const bytes = new Uint8Array(buffer);
  const len = bytes.byteLength;
  
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Сохранение code_verifier в sessionStorage для последующего использования
 * @param verifier code_verifier строка
 */
export function saveCodeVerifier(verifier: string): void {
  sessionStorage.setItem('pkce_code_verifier', verifier);
}

/**
 * Получение сохраненного code_verifier из sessionStorage
 */
export function getCodeVerifier(): string | null {
  return sessionStorage.getItem('pkce_code_verifier');
}

/**
 * Очистка code_verifier из sessionStorage
 */
export function clearCodeVerifier(): void {
  sessionStorage.removeItem('pkce_code_verifier');
}