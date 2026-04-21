import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MesaCardComponent } from './mesa-card';

describe('MesaCard', () => {
  let component: MesaCardComponent;
  let fixture: ComponentFixture<MesaCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MesaCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MesaCardComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
