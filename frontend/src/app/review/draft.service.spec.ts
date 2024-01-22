// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { TestBed } from '@angular/core/testing';

import { DraftService } from './draft.service';

describe('DraftService', () => {
    let service: DraftService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(DraftService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
