import {inject} from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

export const loginGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  if (authService.permissions() === null) {
    return true;
  }
  const router = inject(Router);
  return router.parseUrl('/apps');
};
