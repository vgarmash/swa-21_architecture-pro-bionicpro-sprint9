import Keycloak, { KeycloakConfig } from 'keycloak-js';
import { generateCodeVerifier, createCodeChallenge, saveCodeVerifier, clearCodeVerifier } from './pkceUtils';

/**
 * Конфигурация Keycloak
 */
export interface KeycloakOptions {
  url: string;
  realm: string;
  clientId: string;
}

/**
 * Сервис для работы с Keycloak с поддержкой PKCE
 */
export class KeycloakService {
  private keycloakInstance: Keycloak | null = null;
  private options: KeycloakOptions;

  constructor(options: KeycloakOptions) {
    this.options = options;
  }

  /**
   * Инициализация Keycloak с PKCE
   * @param onAuthenticated Callback при успешной аутентификации
   * @param onLoginRequired Callback при необходимости входа
   */
  async init(onAuthenticated?: () => void, onLoginRequired?: () => void): Promise<void> {
    const config: KeycloakConfig = {
      url: this.options.url,
      realm: this.options.realm,
      clientId: this.options.clientId,
    };

    this.keycloakInstance = new Keycloak(config);

    // Генерация PKCE параметров
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = await createCodeChallenge(codeVerifier);
    
    // Сохраняем code_verifier для последующего обмена
    saveCodeVerifier(codeVerifier);

    // Настройка PKCE параметров в конфиге
    this.keycloakInstance.onReady = (authenticated) => {
      if (authenticated && onAuthenticated) {
        onAuthenticated();
      } else if (!authenticated && onLoginRequired) {
        onLoginRequired();
      }
    };

    this.keycloakInstance.onAuthSuccess = () => {
      if (onAuthenticated) {
        onAuthenticated();
      }
    };

    this.keycloakInstance.onAuthError = () => {
      console.error('Keycloak authentication error');
      clearCodeVerifier();
    };

    // Вход с PKCE параметрами
    await this.keycloakInstance.init({
      pkceMethod: 'S256',
      codeChallenge: codeChallenge,
      codeVerifier: codeVerifier,
      checkLoginIframe: true,
      checkLoginIframeInterval: 5,
    });
  }

  /**
   * Вход в систему
   */
  login(): Promise<void> {
    if (!this.keycloakInstance) {
      throw new Error('Keycloak not initialized');
    }
    
    // Генерация новых PKCE параметров для каждого входа
    const codeVerifier = generateCodeVerifier();
    return createCodeChallenge(codeVerifier).then(codeChallenge => {
      saveCodeVerifier(codeVerifier);
      
      return this.keycloakInstance!.login({
        pkceMethod: 'S256',
        codeChallenge: codeChallenge,
        codeVerifier: codeVerifier,
      });
    });
  }

  /**
   * Выход из системы
   */
  logout(): Promise<void> {
    clearCodeVerifier();
    return this.keycloakInstance?.logout() || Promise.resolve();
  }

  /**
   * Обновление токена
   */
  updateToken(minValidity: number = 60): Promise<boolean> {
    return this.keycloakInstance?.updateToken(minValidity) || Promise.resolve(false);
  }

  /**
   * Получение токена
   */
  getToken(): string | undefined {
    return this.keycloakInstance?.token;
  }

  /**
   * Проверка аутентификации
   */
  isAuthenticated(): boolean {
    return this.keycloakInstance?.authenticated || false;
  }

  /**
   * Получение экземпляра Keycloak
   */
  getInstance(): Keycloak | null {
    return this.keycloakInstance;
  }

  /**
   * Получение информации о пользователе
   */
  async loadUserProfile(): Promise<void> {
    return this.keycloakInstance?.loadUserProfile() || Promise.resolve();
  }

  /**
   * Получение ролей пользователя
   */
  getRealmRoles(): string[] {
    return this.keycloakInstance?.realmAccess?.roles || [];
  }

  /**
   * Проверка наличия роли
   */
  hasRole(role: string): boolean {
    return this.keycloakInstance?.realmAccess?.roles.includes(role) || false;
  }
}