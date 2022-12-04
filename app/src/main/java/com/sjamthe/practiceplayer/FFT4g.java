package com.sjamthe.practiceplayer;
/*
 Real Discrete Fourier Transform
Converted from implementation by Takuya OOURA in C

Copyright:
    Copyright(C) 1996-2001 Takuya OOURA
    email: ooura@mmm.t.u-tokyo.ac.jp
    download: http://momonga.t.u-tokyo.ac.jp/~ooura/fft.html
    You may use, copy, modify this code for any purpose and
    without fee. You may distribute this ORIGINAL package.
 */

public class FFT4g {
    private int[] ip;
    private int n;
    private double[] w;

    public FFT4g(int size) {
        this.n = size;
        double d = size;
        this.ip = new int[((int) Math.sqrt(d / 2.0d)) + 2 + 1];
        this.w = new double[size / 2];
        this.ip[0] = 0;
    }

    public void rdft(int isgn, double[] a) {
        int nw, nc;
        double xi;

        nw = this.ip[0];
        if (this.n > (nw << 2)) {
            nw = this.n >> 2;
            makewt(nw);
        }
        nc = this.ip[1];
        if (this.n > (nc << 2)) {
            nc = this.n >> 2;
            makect(nc, this.w, nw);
        }
        if (isgn >= 0) {
            if (this.n > 4) {
                bitrv2(this.n, a);
                cftfsub(a);
                rftfsub(a, nc, this.w, nw);
            } else if (n == 4) {
                cftfsub(a);
            }
            xi = a[0] - a[1];
            a[0] += a[1];
            a[1] = xi;
        } else {
            a[1] = 0.5 * (a[0] - a[1]);
            a[0] -= a[1];
            if (n > 4) {
                rftbsub(a, nc, this.w, nw);
                bitrv2(this.n, a);
                cftbsub(a);
            } else if (n == 4) {
                cftfsub(a);
            }
        }
    }

    private void cftbsub(double[] a) {
        int j, j1, j2, j3, l;
        double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

        l = 2;
        if (this.n > 8) {
            cft1st(l, a);
            l = 8;
            while ((l << 2) < this.n) {
                cftmdl(l, a);
                l <<= 2;
            }
        }
        if ((l << 2) == this.n) {
            for (j = 0; j < l; j += 2) {
                j1 = j + l;
                j2 = j1 + l;
                j3 = j2 + l;
                x0r = a[j] + a[j1];
                x0i = -a[j + 1] - a[j1 + 1];
                x1r = a[j] - a[j1];
                x1i = -a[j + 1] + a[j1 + 1];
                x2r = a[j2] + a[j3];
                x2i = a[j2 + 1] + a[j3 + 1];
                x3r = a[j2] - a[j3];
                x3i = a[j2 + 1] - a[j3 + 1];
                a[j] = x0r + x2r;
                a[j + 1] = x0i - x2i;
                a[j2] = x0r - x2r;
                a[j2 + 1] = x0i + x2i;
                a[j1] = x1r - x3i;
                a[j1 + 1] = x1i - x3r;
                a[j3] = x1r + x3i;
                a[j3 + 1] = x1i + x3r;
            }
        } else {
            for (j = 0; j < l; j += 2) {
                j1 = j + l;
                x0r = a[j] - a[j1];
                x0i = -a[j + 1] + a[j1 + 1];
                a[j] += a[j1];
                a[j + 1] = -a[j + 1] - a[j1 + 1];
                a[j1] = x0r;
                a[j1 + 1] = x0i;
            }
        }
    }

    private void cftmdl(int l, double[] a) {
        int j, j1, j2, j3, k, k1, k2, m, m2;
        double wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
        double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

        m = l << 2;
        for (j = 0; j < l; j += 2) {
            j1 = j + l;
            j2 = j1 + l;
            j3 = j2 + l;
            x0r = a[j] + a[j1];
            x0i = a[j + 1] + a[j1 + 1];
            x1r = a[j] - a[j1];
            x1i = a[j + 1] - a[j1 + 1];
            x2r = a[j2] + a[j3];
            x2i = a[j2 + 1] + a[j3 + 1];
            x3r = a[j2] - a[j3];
            x3i = a[j2 + 1] - a[j3 + 1];
            a[j] = x0r + x2r;
            a[j + 1] = x0i + x2i;
            a[j2] = x0r - x2r;
            a[j2 + 1] = x0i - x2i;
            a[j1] = x1r - x3i;
            a[j1 + 1] = x1i + x3r;
            a[j3] = x1r + x3i;
            a[j3 + 1] = x1i - x3r;
        }
        wk1r = this.w[2];
        for (j = m; j < l + m; j += 2) {
            j1 = j + l;
            j2 = j1 + l;
            j3 = j2 + l;
            x0r = a[j] + a[j1];
            x0i = a[j + 1] + a[j1 + 1];
            x1r = a[j] - a[j1];
            x1i = a[j + 1] - a[j1 + 1];
            x2r = a[j2] + a[j3];
            x2i = a[j2 + 1] + a[j3 + 1];
            x3r = a[j2] - a[j3];
            x3i = a[j2 + 1] - a[j3 + 1];
            a[j] = x0r + x2r;
            a[j + 1] = x0i + x2i;
            a[j2] = x2i - x0i;
            a[j2 + 1] = x0r - x2r;
            x0r = x1r - x3i;
            x0i = x1i + x3r;
            a[j1] = wk1r * (x0r - x0i);
            a[j1 + 1] = wk1r * (x0r + x0i);
            x0r = x3i + x1r;
            x0i = x3r - x1i;
            a[j3] = wk1r * (x0i - x0r);
            a[j3 + 1] = wk1r * (x0i + x0r);
        }
        k1 = 0;
        m2 = 2 * m;
        for (k = m2; k < this.n; k += m2) {
            k1 += 2;
            k2 = 2 * k1;
            wk2r = this.w[k1];
            wk2i = this.w[k1 + 1];
            wk1r = this.w[k2];
            wk1i = this.w[k2 + 1];
            wk3r = wk1r - 2 * wk2i * wk1i;
            wk3i = 2 * wk2i * wk1r - wk1i;
            for (j = k; j < l + k; j += 2) {
                j1 = j + l;
                j2 = j1 + l;
                j3 = j2 + l;
                x0r = a[j] + a[j1];
                x0i = a[j + 1] + a[j1 + 1];
                x1r = a[j] - a[j1];
                x1i = a[j + 1] - a[j1 + 1];
                x2r = a[j2] + a[j3];
                x2i = a[j2 + 1] + a[j3 + 1];
                x3r = a[j2] - a[j3];
                x3i = a[j2 + 1] - a[j3 + 1];
                a[j] = x0r + x2r;
                a[j + 1] = x0i + x2i;
                x0r -= x2r;
                x0i -= x2i;
                a[j2] = wk2r * x0r - wk2i * x0i;
                a[j2 + 1] = wk2r * x0i + wk2i * x0r;
                x0r = x1r - x3i;
                x0i = x1i + x3r;
                a[j1] = wk1r * x0r - wk1i * x0i;
                a[j1 + 1] = wk1r * x0i + wk1i * x0r;
                x0r = x1r + x3i;
                x0i = x1i - x3r;
                a[j3] = wk3r * x0r - wk3i * x0i;
                a[j3 + 1] = wk3r * x0i + wk3i * x0r;
            }
            wk1r = this.w[k2 + 2];
            wk1i = this.w[k2 + 3];
            wk3r = wk1r - 2 * wk2r * wk1i;
            wk3i = 2 * wk2r * wk1r - wk1i;
            for (j = k + m; j < l + (k + m); j += 2) {
                j1 = j + l;
                j2 = j1 + l;
                j3 = j2 + l;
                x0r = a[j] + a[j1];
                x0i = a[j + 1] + a[j1 + 1];
                x1r = a[j] - a[j1];
                x1i = a[j + 1] - a[j1 + 1];
                x2r = a[j2] + a[j3];
                x2i = a[j2 + 1] + a[j3 + 1];
                x3r = a[j2] - a[j3];
                x3i = a[j2 + 1] - a[j3 + 1];
                a[j] = x0r + x2r;
                a[j + 1] = x0i + x2i;
                x0r -= x2r;
                x0i -= x2i;
                a[j2] = -wk2i * x0r - wk2r * x0i;
                a[j2 + 1] = -wk2i * x0i + wk2r * x0r;
                x0r = x1r - x3i;
                x0i = x1i + x3r;
                a[j1] = wk1r * x0r - wk1i * x0i;
                a[j1 + 1] = wk1r * x0i + wk1i * x0r;
                x0r = x1r + x3i;
                x0i = x1i - x3r;
                a[j3] = wk3r * x0r - wk3i * x0i;
                a[j3 + 1] = wk3r * x0i + wk3i * x0r;
            }
        }
    }

    private void rftbsub(double[] a, int nc, double[] c, int nw) {
        int j, k, kk, ks, m;
        double wkr, wki, xr, xi, yr, yi;

        a[1] = -a[1];
        m = this.n >> 1;
        ks = 2 * nc / m;
        kk = 0;
        for (j = 2; j < m; j += 2) {
            k = this.n - j;
            kk += ks;
            wkr = 0.5 - c[(nw + nc) - kk];
            wki = c[nw + kk];
            xr = a[j] - a[k];
            xi = a[j + 1] + a[k + 1];
            yr = wkr * xr + wki * xi;
            yi = wkr * xi - wki * xr;
            a[j] -= yr;
            a[j + 1] = yi - a[j + 1];
            a[k] += yr;
            a[k + 1] = yi - a[k + 1];
        }
        a[m + 1] = -a[m + 1];
    }

    private void rftfsub(double[] a, int nc, double[] c, int nw) {
        int j, k, kk, ks, m;
        double wkr, wki, xr, xi, yr, yi;

        m = this.n >> 1;
        ks = 2 * nc / m;
        kk = 0;
        for (j = 2; j < m; j += 2) {
            k = this.n - j;
            kk += ks;
            wkr = 0.5 - c[(nw + nc) - kk];
            wki = c[nw + kk];
            xr = a[j] - a[k];
            xi = a[j + 1] + a[k + 1];
            yr = wkr * xr - wki * xi;
            yi = wkr * xi + wki * xr;
            a[j] -= yr;
            a[j + 1] -= yi;
            a[k] += yr;
            a[k + 1] -= yi;
        }
    }

    private void cftfsub(double[] a) {
        int j, j1, j2, j3, l;
        double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

        l = 2;
        if (this.n > 8) {
            cft1st(l, a);
            l = 8;
            while ((l << 2) < this.n) {
                cftmdl(l, a);
                l <<= 2;
            }
        }
        if ((l << 2) == this.n) {
            for (j = 0; j < l; j += 2) {
                j1 = j + l;
                j2 = j1 + l;
                j3 = j2 + l;
                x0r = a[j] + a[j1];
                x0i = a[j + 1] + a[j1 + 1];
                x1r = a[j] - a[j1];
                x1i = a[j + 1] - a[j1 + 1];
                x2r = a[j2] + a[j3];
                x2i = a[j2 + 1] + a[j3 + 1];
                x3r = a[j2] - a[j3];
                x3i = a[j2 + 1] - a[j3 + 1];
                a[j] = x0r + x2r;
                a[j + 1] = x0i + x2i;
                a[j2] = x0r - x2r;
                a[j2 + 1] = x0i - x2i;
                a[j1] = x1r - x3i;
                a[j1 + 1] = x1i + x3r;
                a[j3] = x1r + x3i;
                a[j3 + 1] = x1i - x3r;
            }
        } else {
            for (j = 0; j < l; j += 2) {
                j1 = j + l;
                x0r = a[j] - a[j1];
                x0i = a[j + 1] - a[j1 + 1];
                a[j] += a[j1];
                a[j + 1] += a[j1 + 1];
                a[j1] = x0r;
                a[j1 + 1] = x0i;
            }
        }
    }

    private void cft1st(int l, double[] a) {
        int j, j1, j2, j3, k, k1, k2, m, m2;
        double wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
        double x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

        m = l << 2;
        for (j = 0; j < l; j += 2) {
            j1 = j + l;
            j2 = j1 + l;
            j3 = j2 + l;
            x0r = a[j] + a[j1];
            x0i = a[j + 1] + a[j1 + 1];
            x1r = a[j] - a[j1];
            x1i = a[j + 1] - a[j1 + 1];
            x2r = a[j2] + a[j3];
            x2i = a[j2 + 1] + a[j3 + 1];
            x3r = a[j2] - a[j3];
            x3i = a[j2 + 1] - a[j3 + 1];
            a[j] = x0r + x2r;
            a[j + 1] = x0i + x2i;
            a[j2] = x0r - x2r;
            a[j2 + 1] = x0i - x2i;
            a[j1] = x1r - x3i;
            a[j1 + 1] = x1i + x3r;
            a[j3] = x1r + x3i;
            a[j3 + 1] = x1i - x3r;
        }
        wk1r = this.w[2];
        for (j = m; j < l + m; j += 2) {
            j1 = j + l;
            j2 = j1 + l;
            j3 = j2 + l;
            x0r = a[j] + a[j1];
            x0i = a[j + 1] + a[j1 + 1];
            x1r = a[j] - a[j1];
            x1i = a[j + 1] - a[j1 + 1];
            x2r = a[j2] + a[j3];
            x2i = a[j2 + 1] + a[j3 + 1];
            x3r = a[j2] - a[j3];
            x3i = a[j2 + 1] - a[j3 + 1];
            a[j] = x0r + x2r;
            a[j + 1] = x0i + x2i;
            a[j2] = x2i - x0i;
            a[j2 + 1] = x0r - x2r;
            x0r = x1r - x3i;
            x0i = x1i + x3r;
            a[j1] = wk1r * (x0r - x0i);
            a[j1 + 1] = wk1r * (x0r + x0i);
            x0r = x3i + x1r;
            x0i = x3r - x1i;
            a[j3] = wk1r * (x0i - x0r);
            a[j3 + 1] = wk1r * (x0i + x0r);
        }
        k1 = 0;
        m2 = 2 * m;
        for (k = m2; k < this.n; k += m2) {
            k1 += 2;
            k2 = 2 * k1;
            wk2r = this.w[k1];
            wk2i = this.w[k1 + 1];
            wk1r = this.w[k2];
            wk1i = this.w[k2 + 1];
            wk3r = wk1r - 2 * wk2i * wk1i;
            wk3i = 2 * wk2i * wk1r - wk1i;
            for (j = k; j < l + k; j += 2) {
                j1 = j + l;
                j2 = j1 + l;
                j3 = j2 + l;
                x0r = a[j] + a[j1];
                x0i = a[j + 1] + a[j1 + 1];
                x1r = a[j] - a[j1];
                x1i = a[j + 1] - a[j1 + 1];
                x2r = a[j2] + a[j3];
                x2i = a[j2 + 1] + a[j3 + 1];
                x3r = a[j2] - a[j3];
                x3i = a[j2 + 1] - a[j3 + 1];
                a[j] = x0r + x2r;
                a[j + 1] = x0i + x2i;
                x0r -= x2r;
                x0i -= x2i;
                a[j2] = wk2r * x0r - wk2i * x0i;
                a[j2 + 1] = wk2r * x0i + wk2i * x0r;
                x0r = x1r - x3i;
                x0i = x1i + x3r;
                a[j1] = wk1r * x0r - wk1i * x0i;
                a[j1 + 1] = wk1r * x0i + wk1i * x0r;
                x0r = x1r + x3i;
                x0i = x1i - x3r;
                a[j3] = wk3r * x0r - wk3i * x0i;
                a[j3 + 1] = wk3r * x0i + wk3i * x0r;
            }
            wk1r = this.w[k2 + 2];
            wk1i = this.w[k2 + 3];
            wk3r = wk1r - 2 * wk2r * wk1i;
            wk3i = 2 * wk2r * wk1r - wk1i;
            for (j = k + m; j < l + (k + m); j += 2) {
                j1 = j + l;
                j2 = j1 + l;
                j3 = j2 + l;
                x0r = a[j] + a[j1];
                x0i = a[j + 1] + a[j1 + 1];
                x1r = a[j] - a[j1];
                x1i = a[j + 1] - a[j1 + 1];
                x2r = a[j2] + a[j3];
                x2i = a[j2 + 1] + a[j3 + 1];
                x3r = a[j2] - a[j3];
                x3i = a[j2 + 1] - a[j3 + 1];
                a[j] = x0r + x2r;
                a[j + 1] = x0i + x2i;
                x0r -= x2r;
                x0i -= x2i;
                a[j2] = -wk2i * x0r - wk2r * x0i;
                a[j2 + 1] = -wk2i * x0i + wk2r * x0r;
                x0r = x1r - x3i;
                x0i = x1i + x3r;
                a[j1] = wk1r * x0r - wk1i * x0i;
                a[j1 + 1] = wk1r * x0i + wk1i * x0r;
                x0r = x1r + x3i;
                x0i = x1i - x3r;
                a[j3] = wk3r * x0r - wk3i * x0i;
                a[j3 + 1] = wk3r * x0i + wk3i * x0r;
            }
        }
    }

    private void makewt(int nw) {
        int j, nwh;
        double delta, x, y;

        this.ip[0] = nw;
        this.ip[1] = 1;
        if (nw > 2) {
            nwh = nw >> 1;
            delta = Math.atan(1.0) / nwh;
            this.w[0] = 1.0d;
            this.w[1] = 0.0d;
            this.w[nwh] = Math.cos(delta * nwh);
            this.w[nwh + 1] = this.w[nwh];
            if (nwh > 2) {
                for (j = 2; j < nwh; j += 2) {
                    x = Math.cos(delta * j);
                    y = Math.sin(delta * j);
                    this.w[j] = x;
                    this.w[j + 1] = y;
                    this.w[nw - j] = y;
                    this.w[nw - j + 1] = x;
                }
                bitrv2(nw, this.w);
            }
        }
    }

    private void makect(int nc, double[] c, int nw) {
        int j, nch;
        double delta;

        this.ip[1] = nc;
        if (nc > 1) {
            nch = nc >> 1;
            delta = Math.atan(1.0) / nch;
            c[nw + 0] = Math.cos(delta * nch);
            c[nw + nch] = 0.5d * c[nw + 0];
            for (j = 1; j < nch; j++) {
                c[nw + j] = 0.5d * Math.cos(delta * j);
                c[(nw + nc) - j] = 0.5d * Math.sin(delta * j);
            }
        }
    }

    private void bitrv2(int n, double[] a) {
        int j, j1, k, k1, l, m, m2;
        double xr, xi, yr, yi;

        this.ip[2] = 0;
        l = n;
        m = 1;
        while ((m << 3) < l) {
            l >>= 1;
            for (j = 0; j < m; j++) {
                this.ip[m + j + 2] = this.ip[j + 2] + l;
            }
            m <<= 1;
        }
        m2 = 2 * m;
        if ((m << 3) == l) {
            for (k = 0; k < m; k++) {
                for (j = 0; j < k; j++) {
                    j1 = 2 * j + this.ip[k + 2];
                    k1 = 2 * k + this.ip[j + 2];
                    xr = a[j1];
                    xi = a[j1 + 1];
                    yr = a[k1];
                    yi = a[k1 + 1];
                    a[j1] = yr;
                    a[j1 + 1] = yi;
                    a[k1] = xr;
                    a[k1 + 1] = xi;
                    j1 += m2;
                    k1 += 2 * m2;
                    xr = a[j1];
                    xi = a[j1 + 1];
                    yr = a[k1];
                    yi = a[k1 + 1];
                    a[j1] = yr;
                    a[j1 + 1] = yi;
                    a[k1] = xr;
                    a[k1 + 1] = xi;
                    j1 += m2;
                    k1 -= m2;
                    xr = a[j1];
                    xi = a[j1 + 1];
                    yr = a[k1];
                    yi = a[k1 + 1];
                    a[j1] = yr;
                    a[j1 + 1] = yi;
                    a[k1] = xr;
                    a[k1 + 1] = xi;
                    j1 += m2;
                    k1 += 2 * m2;
                    xr = a[j1];
                    xi = a[j1 + 1];
                    yr = a[k1];
                    yi = a[k1 + 1];
                    a[j1] = yr;
                    a[j1 + 1] = yi;
                    a[k1] = xr;
                    a[k1 + 1] = xi;
                }
                j1 = 2 * k + m2 + this.ip[k + 2];
                k1 = j1 + m2;
                xr = a[j1];
                xi = a[j1 + 1];
                yr = a[k1];
                yi = a[k1 + 1];
                a[j1] = yr;
                a[j1 + 1] = yi;
                a[k1] = xr;
                a[k1 + 1] = xi;
            }
        } else {
            for (k = 1; k < m; k++) {
                for (j = 0; j < k; j++) {
                    j1 = 2 * j + this.ip[k + 2];
                    k1 = 2 * k + this.ip[j + 2];
                    xr = a[j1];
                    xi = a[j1 + 1];
                    yr = a[k1];
                    yi = a[k1 + 1];
                    a[j1] = yr;
                    a[j1 + 1] = yi;
                    a[k1] = xr;
                    a[k1 + 1] = xi;
                    j1 += m2;
                    k1 += m2;
                    xr = a[j1];
                    xi = a[j1 + 1];
                    yr = a[k1];
                    yi = a[k1 + 1];
                    a[j1] = yr;
                    a[j1 + 1] = yi;
                    a[k1] = xr;
                    a[k1 + 1] = xi;
                }
            }
        }
    }

}
