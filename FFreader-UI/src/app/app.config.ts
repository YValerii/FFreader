import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(),
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-icons-round' } }
  ]
};
