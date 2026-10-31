import { HttpInterceptorFn } from '@angular/common/http';

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') {
    return next(req);
  }
  const token = readCookie('XSRF-TOKEN');
  if (!token) return next(req);

  return next(
    req.clone({
      setHeaders: { 'X-XSRF-TOKEN': token },
      withCredentials: true
    })
  );
};