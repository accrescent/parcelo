# SPDX-FileCopyrightText: © 2026 Logan Magee
#
# SPDX-License-Identifier: AGPL-3.0-only

cmake_minimum_required(VERSION 3.22.1)

project(nativelib)

add_library(nativelib SHARED src/main/cpp/nativelib.c)
