// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { TestBed } from '@angular/core/testing';

import { UpdateService } from './update.service';

describe('UpdateService', () => {
    let service: UpdateService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(UpdateService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
