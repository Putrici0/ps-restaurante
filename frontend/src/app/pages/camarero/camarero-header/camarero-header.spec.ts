import { ComponentFixture, TestBed } from '@angular/core/testing';

<<<<<<<< HEAD:frontend/src/app/pages/camarero/menu/menu.spec.ts
import { MenuPage } from './menu-page';

describe('MenuPage', () => {
  let component: MenuPage;
  let fixture: ComponentFixture<MenuPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MenuPage],
    }).compileComponents();

    fixture = TestBed.createComponent(MenuPage);
========
import { CamareroHeader } from './camarero-header';

describe('CamareroHeader', () => {
  let component: CamareroHeader;
  let fixture: ComponentFixture<CamareroHeader>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CamareroHeader],
    }).compileComponents();

    fixture = TestBed.createComponent(CamareroHeader);
>>>>>>>> Juan-camarero:frontend/src/app/pages/camarero/camarero-header/camarero-header.spec.ts
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
