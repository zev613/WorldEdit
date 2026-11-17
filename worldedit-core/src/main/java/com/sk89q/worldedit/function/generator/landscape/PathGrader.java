/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.function.generator.landscape;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Grades a sequence of terrain heights along a route so that consecutive heights differ by at
 * most a given step while staying as close to the terrain as possible.
 */
public final class PathGrader {

    private PathGrader() {
    }

    /**
     * Grade a height profile.
     *
     * <p>The profile is first smoothed with a moving average of the given radius. Then the
     * largest 1-Lipschitz (scaled by {@code maxStep}) function below and the smallest above the
     * smoothed profile are computed; their midpoint has the smallest possible maximum deviation
     * from the profile among all functions satisfying the step constraint. Rounding preserves the
     * constraint for integer steps.</p>
     *
     * @param raw the terrain heights along the route
     * @param smoothRadius the radius of the moving average (0 for none)
     * @param maxStep the maximum height difference between consecutive entries (at least 1)
     * @return the graded heights, one for each entry of {@code raw}
     */
    public static int[] grade(int[] raw, int smoothRadius, int maxStep) {
        checkArgument(smoothRadius >= 0, "smoothRadius must be >= 0");
        checkArgument(maxStep >= 1, "maxStep must be >= 1");
        int n = raw.length;
        int[] result = new int[n];
        if (n == 0) {
            return result;
        }
        double[] smooth = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - smoothRadius); j <= Math.min(n - 1, i + smoothRadius); j++) {
                sum += raw[j];
                count++;
            }
            smooth[i] = sum / count;
        }

        double[] lower = smooth.clone();
        double[] upper = smooth.clone();
        for (int i = 1; i < n; i++) {
            lower[i] = Math.max(lower[i], lower[i - 1] - maxStep);
            upper[i] = Math.min(upper[i], upper[i - 1] + maxStep);
        }
        for (int i = n - 2; i >= 0; i--) {
            lower[i] = Math.max(lower[i], lower[i + 1] - maxStep);
            upper[i] = Math.min(upper[i], upper[i + 1] + maxStep);
        }
        for (int i = 0; i < n; i++) {
            result[i] = (int) Math.floor((lower[i] + upper[i]) / 2 + 0.5);
        }
        // Rounding a function whose steps are at most maxStep never yields a step above maxStep,
        // but guard against floating point surprises anyway.
        for (int i = 1; i < n; i++) {
            if (result[i] > result[i - 1] + maxStep) {
                result[i] = result[i - 1] + maxStep;
            } else if (result[i] < result[i - 1] - maxStep) {
                result[i] = result[i - 1] - maxStep;
            }
        }
        return result;
    }
}
