// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { AbstractControl, isFormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';

export const atLeastOne = (validator: ValidatorFn) => (
    control: AbstractControl,
): ValidationErrors | null => {
    if (isFormGroup(control)) {
        const hasAtLeastOne = Object
            .keys(control.controls)
            .some(k => !validator(control.controls[k]));

        return hasAtLeastOne ? null : { atLeastOne: true };
    } else {
        return { atLeastOne: true };
    }
};
