import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Node 22+ ships its own (unusable without a flag) localStorage global that
// shadows jsdom's implementation, so provide a plain in-memory Storage shim.
function ensureStorage(name) {
  try {
    const existing = globalThis[name];
    if (existing && typeof existing.clear === 'function') return;
  } catch {
    // fall through to the shim
  }
  const store = new Map();
  const shim = {
    getItem: (key) => (store.has(String(key)) ? store.get(String(key)) : null),
    setItem: (key, value) => store.set(String(key), String(value)),
    removeItem: (key) => store.delete(String(key)),
    clear: () => store.clear(),
    key: (index) => [...store.keys()][index] ?? null,
    get length() {
      return store.size;
    },
  };
  Object.defineProperty(globalThis, name, { value: shim, configurable: true });
}

ensureStorage('localStorage');
ensureStorage('sessionStorage');

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  localStorage.clear();
});
