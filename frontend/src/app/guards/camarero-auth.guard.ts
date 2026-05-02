import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { CamareroAuthService } from '../services/camarero-auth.service';

export const camareroAuthGuard: CanActivateFn = async () => {
  const authService = inject(CamareroAuthService);
  const router = inject(Router);

  try {
    const usuarioActual = authService.obtenerUsuarioActual();
    if (usuarioActual) {
      const autorizado = await authService.esCamarero(usuarioActual.uid);
      return autorizado ? true : router.createUrlTree(['/camarero/login']);
    }

    const usuario = await authService.esperarEstadoAuth();
    if (!usuario) {
      return router.createUrlTree(['/camarero/login']);
    }

    const autorizado = await authService.esCamarero(usuario.uid);
    return autorizado ? true : router.createUrlTree(['/camarero/login']);
  } catch {
    return router.createUrlTree(['/camarero/login']);
  }
};
