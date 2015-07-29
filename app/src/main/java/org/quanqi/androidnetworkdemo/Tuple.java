package org.quanqi.androidnetworkdemo;

import java.util.List;

/**
 * By cindy on 7/24/15 11:04 AM.
 */
public class Tuple<X, Y> {

    private List<X> xList;
    private List<Y> yList;


    public X getX(int index) {
        return xList.get(index);
    }

    public Y getY(int index) {
        return yList.get(index);
    }
}
