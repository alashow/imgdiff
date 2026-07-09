import { createApp } from "vue";
import "./style.css";
import App from "./App.vue";

// Polyfill Ctrl/Cmd+A for text inputs inside Compose Desktop WebView,
// where the host OS may not forward those shortcuts to the embedded browser.
document.addEventListener('keydown', (e) => {
  const target = e.target as HTMLElement;
  const isEditable = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA';
  if (!isEditable) return;
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
    (target as HTMLInputElement).select();
    e.preventDefault();
  }
}, true);

createApp(App).mount("#app");
