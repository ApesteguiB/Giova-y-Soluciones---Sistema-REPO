import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { PublicSiteService } from './public-site.service';

/**
 * Las rutas declaran solo el nombre de la sección ("Productos"); la empresa la
 * pone el sitio resuelto por dominio, para que el título no diga GIOVA en el
 * sitio de otra empresa.
 */
@Injectable({ providedIn: 'root' })
export class BrandTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly site = inject(PublicSiteService);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const section = this.buildTitle(snapshot);
    const brand = this.site.brandName();
    this.title.setTitle(section ? `${section} | ${brand}` : this.site.site().siteTitle);
  }
}
