/* Comportamiento común del sitio público de Soluciones Informática TI. */
(function () {
  'use strict';

  var nav = document.getElementById('nav');
  if (nav) {
    var marcar = function () { nav.classList.toggle('scrolled', window.scrollY > 40); };
    window.addEventListener('scroll', marcar, { passive: true });
    marcar();
  }

  // Aparición suave de las secciones al entrar en pantalla.
  var pendientes = document.querySelectorAll('.rv:not(.in)');
  if ('IntersectionObserver' in window) {
    var observador = new IntersectionObserver(function (entradas) {
      entradas.forEach(function (entrada) {
        if (entrada.isIntersecting) {
          entrada.target.classList.add('in');
          observador.unobserve(entrada.target);
        }
      });
    }, { threshold: 0.12 });
    pendientes.forEach(function (el) { observador.observe(el); });
  } else {
    pendientes.forEach(function (el) { el.classList.add('in'); });
  }

  // Las fotos de la portada vienen de internet. Si alguna no carga (sin
  // conexión), su recuadro muestra una ilustración propia en vez de quedar vacío.
  document.querySelectorAll('.hero-photo img, .svc-img img, .work-cell img').forEach(function (img) {
    var sinFoto = function () {
      var caja = img.closest('.hero-photo, .svc-img, .work-cell');
      if (caja) caja.classList.add('sin-foto');
    };
    if (img.complete && img.naturalWidth === 0) sinFoto();
    img.addEventListener('error', sinFoto);
  });
})();
