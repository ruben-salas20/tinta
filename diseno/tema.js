// Solo para las maquetas: #oscuro o #claro en la dirección fuerza el tema.
const t = { '#oscuro': 'dark', '#claro': 'light' }[location.hash];
if (t) document.documentElement.dataset.theme = t;
