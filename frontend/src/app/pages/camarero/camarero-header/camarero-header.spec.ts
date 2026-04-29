import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CamareroHeader } from './camarero-header';

describe('CamareroHeader', () => {
  let component: CamareroHeader;
  let fixture: ComponentFixture<CamareroHeader>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CamareroHeader],
    }).compileComponents();

    fixture = TestBed.createComponent(CamareroHeader);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
