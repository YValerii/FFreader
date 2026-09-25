import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Fanfic, PageResponse } from '../models/fanfic.model';

@Injectable({ providedIn: 'root' })
export class FanficService {
  private readonly baseUrl = `${environment.apiBaseUrl}/fanfics`;

  constructor(private http: HttpClient) {}

  create(url: string): Observable<Fanfic> {
    return this.http.post<Fanfic>(this.baseUrl, { url });
  }

  list(page: number, size: number): Observable<PageResponse<Fanfic>> {
    return this.http.get<PageResponse<Fanfic>>(this.baseUrl, {
      params: { page, size }
    });
  }

  get(id: string): Observable<Fanfic> {
    return this.http.get<Fanfic>(`${this.baseUrl}/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  downloadUrl(id: string): string {
    return `${this.baseUrl}/${id}/download`;
  }

  download(id: string): Observable<Blob> {
    return this.http.get(this.downloadUrl(id), { responseType: 'blob' });
  }
}
