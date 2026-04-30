import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Platos } from './platos';

describe('Platos', () => {
  let component: Platos;
  let fixture: ComponentFixture<Platos>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Platos],
    }).compileComponents();

    fixture = TestBed.createComponent(Platos);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
