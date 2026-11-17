/* Catálogo público de Soluciones Informática TI: filtros, búsqueda y ficha. */
(function () {
  'use strict';

  var productos = window.CATALOGO_SOLUCIONES || [];
  var grid = document.getElementById('mainGrid');
  var modal = document.getElementById('prodModal');
  var contador = document.getElementById('count');
  var buscador = document.getElementById('searchInput');
  if (!grid || !modal) return;

  // Ilustraciones por tipo de producto (se usan cuando no hay foto propia).
  var ICONOS = {
  laptop:'<rect x="36" y="24" width="48" height="30" rx="2" fill="#fff" stroke="#64748b" stroke-width="2"/><rect x="40" y="28" width="40" height="22" fill="#e86a1c" opacity=".16"/><path d="M30 58h60l-4-4H34z" fill="#cbd5e1" stroke="#64748b" stroke-width="2" stroke-linejoin="round"/>',
  monitor:'<rect x="34" y="22" width="52" height="34" rx="2" fill="#fff" stroke="#64748b" stroke-width="2"/><rect x="40" y="27" width="40" height="24" fill="#e86a1c" opacity=".16"/><path d="M54 56h12v5h9v2H45v-2h9z" fill="#cbd5e1" stroke="#64748b" stroke-width="2" stroke-linejoin="round"/>',
  desktop:'<rect x="46" y="20" width="28" height="50" rx="3" fill="#fff" stroke="#64748b" stroke-width="2"/><circle cx="60" cy="28" r="2.4" fill="#e86a1c"/><rect x="52" y="36" width="16" height="3" fill="#cbd5e1"/><rect x="52" y="42" width="16" height="3" fill="#cbd5e1"/><circle cx="60" cy="56" r="6" fill="none" stroke="#94a3b8" stroke-width="2"/>',
  cpu:'<rect x="44" y="29" width="32" height="32" rx="3" fill="#fff" stroke="#64748b" stroke-width="2"/><rect x="52" y="37" width="16" height="16" rx="2" fill="#e86a1c" opacity=".25" stroke="#64748b" stroke-width="1.4"/><g stroke="#64748b" stroke-width="2"><path d="M50 29v-5M60 29v-5M70 29v-5M50 61v5M60 61v5M70 61v5M44 40h-5M44 50h-5M76 40h5M76 50h5"/></g>',
  board:'<rect x="34" y="24" width="52" height="42" rx="2" fill="#fff" stroke="#64748b" stroke-width="2"/><rect x="40" y="30" width="13" height="13" rx="1" fill="#e86a1c" opacity=".22"/><rect x="58" y="30" width="22" height="4" fill="#cbd5e1"/><rect x="58" y="38" width="22" height="4" fill="#cbd5e1"/><rect x="40" y="50" width="40" height="10" rx="1" fill="#eef2f7" stroke="#94a3b8"/>',
  ram:'<rect x="40" y="30" width="40" height="30" rx="2" fill="#fff" stroke="#64748b" stroke-width="2"/><g fill="#cbd5e1"><rect x="45" y="35" width="4" height="14"/><rect x="52" y="35" width="4" height="14"/><rect x="59" y="35" width="4" height="14"/><rect x="66" y="35" width="4" height="14"/><rect x="73" y="35" width="4" height="14"/></g><rect x="40" y="54" width="40" height="4" fill="#e86a1c" opacity=".3"/>',
  ssd:'<rect x="38" y="30" width="44" height="30" rx="3" fill="#fff" stroke="#64748b" stroke-width="2"/><rect x="44" y="36" width="24" height="18" rx="2" fill="#e86a1c" opacity=".16"/><g fill="#94a3b8"><rect x="72" y="40" width="3" height="4"/><rect x="72" y="47" width="3" height="4"/></g>',
  psu:'<rect x="36" y="28" width="48" height="34" rx="3" fill="#fff" stroke="#64748b" stroke-width="2"/><circle cx="54" cy="45" r="11" fill="none" stroke="#64748b" stroke-width="2"/><g stroke="#94a3b8" stroke-width="2"><path d="M54 36v18M45 45h18"/></g><rect x="72" y="40" width="6" height="10" fill="#e86a1c" opacity=".3"/>',
  keyboard:'<rect x="30" y="34" width="60" height="26" rx="3" fill="#fff" stroke="#64748b" stroke-width="2"/><g fill="#cbd5e1"><rect x="35" y="39" width="7" height="6" rx="1"/><rect x="45" y="39" width="7" height="6" rx="1"/><rect x="55" y="39" width="7" height="6" rx="1"/><rect x="65" y="39" width="7" height="6" rx="1"/><rect x="75" y="39" width="7" height="6" rx="1"/><rect x="35" y="48" width="47" height="6" rx="1"/></g>',
  mouse:'<path d="M60 28c10 0 15 8 15 20s-5 18-15 18-15-6-15-18 5-20 15-20z" fill="#fff" stroke="#64748b" stroke-width="2"/><path d="M60 30v14" stroke="#64748b" stroke-width="2"/><rect x="57" y="34" width="6" height="8" rx="3" fill="#e86a1c" opacity=".4"/>',
  case:'<rect x="44" y="20" width="32" height="50" rx="3" fill="#fff" stroke="#64748b" stroke-width="2"/><rect x="50" y="26" width="20" height="3" fill="#cbd5e1"/><rect x="50" y="32" width="20" height="3" fill="#cbd5e1"/><circle cx="60" cy="52" r="7" fill="none" stroke="#e86a1c" stroke-width="2" opacity=".5"/>',
  box:'<path d="M60 26l22 11v22L60 70 38 59V37z" fill="#fff" stroke="#64748b" stroke-width="2" stroke-linejoin="round"/><path d="M60 26v44M38 37l22 11 22-11" fill="none" stroke="#64748b" stroke-width="2"/>'
    };

  function ilustracion(producto) {
    var texto = (producto.nombre + ' ' + producto.cat).toLowerCase();
    var tipo = 'box';
    if (/notebook|laptop|expertbook|probook|victus|loq|thinkbook/.test(texto)) tipo = 'laptop';
    else if (/monitor/.test(texto)) tipo = 'monitor';
    else if (/thinkcentre|desktop|neo 50|\bpc\b/.test(texto)) tipo = 'desktop';
    else if (/core i|ryzen|procesador|14900/.test(texto)) tipo = 'cpu';
    else if (/mainboard|motherboard|placa|prime|b760|h610|q670/.test(texto)) tipo = 'board';
    else if (/ram|ddr|memoria|fury|vengeance|micron/.test(texto)) tipo = 'ram';
    else if (/ssd|nvme|m\.2|disco|a400|pm9b1/.test(texto)) tipo = 'ssd';
    else if (/fuente|750w|180w|psu/.test(texto)) tipo = 'psu';
    else if (/teclado|k70|mk120|hp 125/.test(texto)) tipo = 'keyboard';
    else if (/mouse|ironclaw/.test(texto)) tipo = 'mouse';
    else if (/case|chasis|rx260|sff/.test(texto)) tipo = 'case';
    return 'data:image/svg+xml,' + encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 90"><rect width="120" height="90" fill="#eef2f7"/>' +
      ICONOS[tipo] + '</svg>');
  }

  function crear(etiqueta, clase, texto) {
    var el = document.createElement(etiqueta);
    if (clase) el.className = clase;
    if (texto !== undefined) el.textContent = texto;
    return el;
  }

  function imagen(producto) {
    var img = document.createElement('img');
    img.alt = producto.nombre;
    img.loading = 'lazy';
    var respaldo = ilustracion(producto);
    if (producto.foto) {
      img.addEventListener('error', function alFallar() {
        img.removeEventListener('error', alFallar);
        img.src = respaldo;
      });
      img.src = '/soluciones/productos/' + encodeURIComponent(producto.foto);
    } else {
      img.src = respaldo;
    }
    return img;
  }

  function filtroActivo() {
    var activo = document.querySelector('.filter-btn.active');
    return activo ? activo.dataset.filter : 'todos';
  }

  function pintar() {
    var filtro = filtroActivo();
    var termino = (buscador ? buscador.value : '').trim().toLowerCase();
    var lista = productos.filter(function (p) {
      var porCategoria = filtro === 'todos' || p.cat === filtro;
      var porTexto = !termino || (p.nombre + ' ' + p.cat + ' ' + p.desc).toLowerCase().indexOf(termino) !== -1;
      return porCategoria && porTexto;
    });
    if (filtro === 'todos') lista.sort(function (a, b) { return a.nombre.localeCompare(b.nombre, 'es'); });

    grid.replaceChildren();
    if (!lista.length) {
      var vacio = crear('div', 'empty');
      var esWorkstation = filtro === 'Workstation';
      vacio.appendChild(crear('h2', '', esWorkstation ? 'Próximamente' : 'Sin resultados'));
      vacio.appendChild(crear('p', '', esWorkstation
        ? 'Para Workstations escríbenos desde la sección Contacto.'
        : 'Prueba con otro término o categoría.'));
      grid.appendChild(vacio);
      if (contador) contador.textContent = '';
      return;
    }
    if (contador) {
      contador.textContent = lista.length + (lista.length === 1 ? ' producto disponible' : ' productos disponibles');
    }
    lista.forEach(function (p) {
      var tarjeta = crear('article', 'card');
      var foto = crear('div', 'card-img');
      foto.appendChild(imagen(p));
      var cuerpo = crear('div', 'card-body');
      cuerpo.appendChild(crear('span', 'card-cat', p.cat));
      cuerpo.appendChild(crear('h3', 'card-title', p.nombre));
      cuerpo.appendChild(crear('p', 'card-desc', p.desc));
      var boton = crear('button', 'btn-detail', 'Ver especificaciones');
      boton.type = 'button';
      boton.addEventListener('click', function () { abrir(p); });
      cuerpo.appendChild(boton);
      tarjeta.appendChild(foto);
      tarjeta.appendChild(cuerpo);
      grid.appendChild(tarjeta);
    });
  }

  var focoPrevio = null;

  function abrir(p) {
    var marco = document.getElementById('mImg');
    marco.className = 'modal-pic';
    marco.replaceChildren(imagen(p));
    document.getElementById('mCat').textContent = p.cat;
    document.getElementById('mTitle').textContent = p.nombre;
    var specs = document.getElementById('mSpecs');
    specs.replaceChildren();
    Object.keys(p.detalles || {}).forEach(function (seccion) {
      var bloque = crear('div', 'spec');
      bloque.appendChild(crear('h4', '', seccion));
      var tabla = document.createElement('table');
      Object.keys(p.detalles[seccion]).forEach(function (clave) {
        var fila = document.createElement('tr');
        fila.appendChild(crear('td', '', clave));
        fila.appendChild(crear('td', '', p.detalles[seccion][clave]));
        tabla.appendChild(fila);
      });
      bloque.appendChild(tabla);
      specs.appendChild(bloque);
    });
    focoPrevio = document.activeElement;
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
    var cerrarBtn = document.getElementById('mClose');
    if (cerrarBtn) cerrarBtn.focus();
  }

  function cerrar() {
    if (!modal.classList.contains('active')) return;
    modal.classList.remove('active');
    document.body.style.overflow = '';
    if (focoPrevio && focoPrevio.focus) focoPrevio.focus();
  }

  document.querySelectorAll('.filter-btn').forEach(function (boton) {
    boton.addEventListener('click', function () {
      var activo = document.querySelector('.filter-btn.active');
      if (activo) activo.classList.remove('active');
      boton.classList.add('active');
      pintar();
    });
  });
  if (buscador) buscador.addEventListener('input', pintar);
  var botonCerrar = document.getElementById('mClose');
  if (botonCerrar) botonCerrar.addEventListener('click', cerrar);
  modal.addEventListener('click', function (evento) { if (evento.target === modal) cerrar(); });
  document.addEventListener('keydown', function (evento) { if (evento.key === 'Escape') cerrar(); });

  var nav = document.getElementById('nav');
  if (nav) {
    window.addEventListener('scroll', function () { nav.classList.toggle('scrolled', window.scrollY > 40); }, { passive: true });
  }

  pintar();
})();
