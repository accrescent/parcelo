import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppListComponent } from './app-list.component';

describe('AppListComponent', () => {
    let component: AppListComponent;
    let fixture: ComponentFixture<AppListComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AppListComponent]
        });
        fixture = TestBed.createComponent(AppListComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
