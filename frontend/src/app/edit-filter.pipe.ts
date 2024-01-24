// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { Pipe, PipeTransform } from '@angular/core';

import { Edit, EditStatus } from './edit';

@Pipe({
    name: 'editFilter',
    standalone: true,
    pure: false,
})
export class EditFilterPipe implements PipeTransform {
    transform(edits: Edit[], showRejected: boolean, showPublished: boolean): Edit[] {
        const filter = (edit: Edit): boolean => {
            return !(
                edit.status === EditStatus.Rejected && !showRejected ||
                edit.status === EditStatus.Published && !showPublished
            );
        };

        return edits.filter(edit => filter(edit));
    }
}
