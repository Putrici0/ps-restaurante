import { ComponentFixture, TestBed } from '@angular/core/testing';
import {MenuCamarero} from './menu';
import {RouterModule} from '@angular/router';

describe('MenuCamarero', () => {
  let component: MenuCamarero;
  let fixture: ComponentFixture<MenuCamarero>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MenuCamarero, RouterModule.forRoot([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MenuCamarero);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
