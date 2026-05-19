import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EstadisticasService {
  private jsonUrl = 'http://localhost:7070/debug/get-stats-data';

  constructor(private http: HttpClient) {
  }

  getHistorico(): Observable<any> {
    return this.http.get(this.jsonUrl);
  }
}
