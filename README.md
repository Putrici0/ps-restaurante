# 🍽️ PS Restaurante

Aplicación de gestión de restaurante con:

* **Backend:** Java + Maven + Javalin + Firebase/Firestore
* **Frontend:** Angular

---

## Requisitos previos

Antes de ejecutar el proyecto, asegúrate de tener instalado:

* **Java JDK 26**
* **Maven**
* **Node.js**
* **npm**
---

## Importante antes de arrancar el backend

El backend usa **Firebase** y busca un archivo llamado:

```bash
firebase-key.json
```

Este archivo debe estar disponible en:

```bash
backend/src/main/resources/firebase-key.json
```

Si no existe, el backend lanzará un error al iniciar.

---

# 1. Ejecutar el backend

## Ejecutar el backend

Ejecutar el `Main.java`.

## Puerto del backend

El backend arranca en:

```bash
http://localhost:7070
```

---

# 2. Ejecutar el frontend

## Entrar en la carpeta del frontend

```bash
cd frontend
```

## Instalar dependencias

```bash
npm install
```

---

## Opción para build de producción

Compilar:

```bash
npm run build
```

Luego servir la carpeta generada con `serve`:

```bash
npm install -g serve
cd dist/frontend/browser
serve -s . -l 80
```

Esto dejará el frontend accesible en:

```bash
http://localhost
```
---

---

# Autores

Alejandro Hernández Delgado 
Carlos Alonso Rodríguez 
Pablo Llopis Parrilla 
Joan Martínez Perdomo 
Esther Viera Rivero 
Juan López Ramírez 

---


