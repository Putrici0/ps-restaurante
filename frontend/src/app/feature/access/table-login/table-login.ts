import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';

@Component({
  selector: 'app-table-login',
  standalone: true,
  // Esta línea de imports soluciona el error de "Can't bind to 'formGroup'"
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './table-login.html',
  styleUrls: ['./table-login.css'],
})
export class TableLogin implements OnInit {
  // <-- Tu clase se llama TableLogin
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  // Aquí definimos las variables que el HTML está buscando
  tableId = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  isLoading = signal<boolean>(false);

  loginForm = this.fb.group({
    password: ['', [Validators.required]],
  });

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    this.tableId.set(id);
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.isLoading.set(true);
      this.errorMessage.set(null);

      const password = this.loginForm.value.password;
      const mesa = this.tableId();

      // Simulamos que comprueba con el backend
      setTimeout(() => {
        if (password === '1234') {
          this.router.navigate(['/menu', mesa]);
        } else {
          this.errorMessage.set('La contraseña no es correcta.');
          this.isLoading.set(false);
        }
      }, 1000);
    }
  }
}
