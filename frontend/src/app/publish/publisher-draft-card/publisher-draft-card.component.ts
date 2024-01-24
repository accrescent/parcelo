// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { Draft } from '../../draft';

@Component({
    selector: 'app-publisher-draft-card',
    standalone: true,
    imports: [MatButtonModule, MatCardModule],
    templateUrl: './publisher-draft-card.component.html',
})
export class PublisherDraftCardComponent {
    @Input({ required: true }) draft!: Draft;
    @Output() publish = new EventEmitter<string>();

    onPublish(): void {
        this.publish.emit(this.draft.id);
    }
}
