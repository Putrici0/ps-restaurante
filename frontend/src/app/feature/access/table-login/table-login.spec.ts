import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';

@Component({
  selector: 'app-table-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './table-login.component.html',
  styleUrls: ['./table-login.component.css']
})
export class TableLoginComponent implements OnInit {
  // Inyección de dependencias moderna
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  // Signals para manejar el estado en la interfaz
  tableId = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  isLoading = signal<boolean>(false);

  // Creamos el formulario para la contraseña
  loginForm = this.fb.group({
    password: ['', [Validators.required]]
  });

  ngOnInit() {
    // Capturamos el número de la mesa de la URL (ej. /acceso/5)
    const id = this.route.snapshot.paramMap.get('id');
    this.tableId.set(id);
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.isLoading.set(true);
      this.errorMessage.set(null);

      const password = this.loginForm.value.password;
      const mesa = this.tableId();

      // AQUÍ IRÍA LA LLAMADA A TU BACKEND
      // Simulamos la respuesta por ahora:
      setTimeout(() => {
        if (password === '1234') { // Contraseña de prueba
          // Si el backend dice que es correcto, navegamos a la carta
          this.router.navigate(['/menu', mesa]);
        } else {
          // Si falla, mostramos error
          this.errorMessage.set('La contraseña no es correcta. Pregúntale al camarero.');
          this.isLoading.set(false);
        }
      }, 1000);
    }
  }
}
