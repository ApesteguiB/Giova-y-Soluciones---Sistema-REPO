/* Acceso de trabajadores (Intranet de Soluciones Informática TI).
   Usa la misma API del sistema: pide el token CSRF, inicia sesión y entra al
   panel. La sesión viaja en una cookie HttpOnly que este script no puede leer. */
(function () {
  'use strict';

  var formulario = document.getElementById('loginForm');
  var aviso = document.getElementById('msg');
  var boton = document.getElementById('submitBtn');
  var clave = document.getElementById('pass');
  var alternar = document.getElementById('tgl');
  if (!formulario) return;

  function mostrar(texto, correcto) {
    aviso.textContent = texto;
    aviso.className = 'msg ' + (correcto ? 'ok' : 'err');
  }

  if (alternar) {
    alternar.addEventListener('click', function () {
      var oculta = clave.type === 'password';
      clave.type = oculta ? 'text' : 'password';
      alternar.textContent = oculta ? 'Ocultar' : 'Ver';
      alternar.setAttribute('aria-pressed', String(oculta));
    });
  }

  function destino(sesion) {
    if (sesion && sesion.mustChangePassword) return '/trabajadores/cambiar-contrasena';
    if (sesion && sesion.roleCode === 'SUPERADMIN') return '/plataforma';
    return '/operaciones';
  }

  // Si ya hay una sesión abierta no hace falta volver a pedir la contraseña.
  fetch('/api/v1/auth/me', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
    .then(function (respuesta) { return respuesta.ok ? respuesta.json() : null; })
    .then(function (sesion) {
      if (sesion && sesion.username) {
        mostrar('Ya tiene una sesión abierta como ' + sesion.username + '. Entrando…', true);
        window.location.assign(destino(sesion));
      }
    })
    .catch(function () { /* sin API disponible: se queda en el formulario */ });

  var enviando = false;
  formulario.addEventListener('submit', function (evento) {
    evento.preventDefault();
    if (enviando) return;
    var usuario = document.getElementById('user').value.trim();
    var contrasena = clave.value;
    if (!usuario || !contrasena) {
      mostrar('Ingrese usuario y contraseña.');
      return;
    }
    enviando = true;
    boton.disabled = true;
    boton.textContent = 'Validando…';
    aviso.className = 'msg';

    fetch('/api/v1/auth/csrf', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
      .then(function (respuesta) {
        if (!respuesta.ok) throw new Error('csrf');
        return respuesta.json();
      })
      .then(function (csrf) {
        return fetch('/api/v1/auth/login', {
          method: 'POST',
          credentials: 'same-origin',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-XSRF-TOKEN': csrf.token || '',
            Accept: 'application/json'
          },
          body: new URLSearchParams({ username: usuario, password: contrasena })
        });
      })
      .then(function (respuesta) {
        if (respuesta.ok) {
          return respuesta.json().then(function (sesion) {
            mostrar('Acceso correcto. Entrando al sistema…', true);
            window.location.assign(destino(sesion));
          });
        }
        clave.value = '';
        if (respuesta.status === 401 || respuesta.status === 403) {
          mostrar('Usuario o contraseña incorrectos. Revise los datos.');
        } else if (respuesta.status === 429) {
          mostrar('Demasiados intentos. Espere un minuto y vuelva a intentar.');
        } else {
          mostrar('No se pudo iniciar sesión en este momento. Intente de nuevo.');
        }
      })
      .catch(function () {
        mostrar('No hay conexión con el sistema. Verifique que el servicio esté encendido.');
      })
      .then(function () {
        enviando = false;
        boton.disabled = false;
        boton.textContent = 'Ingresar';
      });
  });
})();
