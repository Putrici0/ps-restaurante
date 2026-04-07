import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MesaDetalle } from './mesa-detalle';

describe('MesaDetalle', () => {
  let component: MesaDetalle;
  let fixture: ComponentFixture<MesaDetalle>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MesaDetalle],
    }).compileComponents();

    fixture = TestBed.createComponent(MesaDetalle);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
