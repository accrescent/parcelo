// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PublishScreenComponent } from './publish-screen.component';

describe('PublishScreenComponent', () => {
    let component: PublishScreenComponent;
    let fixture: ComponentFixture<PublishScreenComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PublishScreenComponent]
        });
        fixture = TestBed.createComponent(PublishScreenComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
