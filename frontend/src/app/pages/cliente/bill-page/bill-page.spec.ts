import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BillPage } from './bill-page';

describe('BillPage', () => {
  let component: BillPage;
  let fixture: ComponentFixture<BillPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BillPage],
    }).compileComponents();

    fixture = TestBed.createComponent(BillPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
