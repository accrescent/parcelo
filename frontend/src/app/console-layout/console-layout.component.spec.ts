import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConsoleLayoutComponent } from './console-layout.component';

describe('ConsoleLayoutComponent', () => {
    let component: ConsoleLayoutComponent;
    let fixture: ComponentFixture<ConsoleLayoutComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ ConsoleLayoutComponent ]
        })
            .compileComponents();

        fixture = TestBed.createComponent(ConsoleLayoutComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
