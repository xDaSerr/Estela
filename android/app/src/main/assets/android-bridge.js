// Imita la API de pywebview (window.pywebview.api) para que ui/index.html funcione igual en Android.
(() => {
  if (window.pywebview) return;

  const pending = {};
  let nextId = 0;

  window.__estelaResolve = (id, result) => {
    const resolve = pending[id];
    if (resolve) {
      delete pending[id];
      resolve(result);
    }
  };

  const call = (method, ...args) =>
    new Promise((resolve) => {
      const id = ++nextId;
      pending[id] = resolve;
      EstelaAndroid.call(id, method, JSON.stringify(args));
    });

  const api = {};
  ["get_folder", "choose_folder", "open_folder", "get_info", "download", "check_update", "restart"].forEach(
    (method) => (api[method] = (...args) => call(method, ...args)),
  );
  window.pywebview = { api };

  // En Android siempre se guarda en Descargas/Estela
  const style = document.createElement("style");
  style.textContent = "#chooseBtn { display: none; }";
  document.head.appendChild(style);

  window.dispatchEvent(new Event("pywebviewready"));
})();
