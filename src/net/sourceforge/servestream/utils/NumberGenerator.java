/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2010 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import android.util.Log;

public class NumberGenerator {
	
	/**
	 * Default constructor
	 */
	public NumberGenerator() {
	
	}
	
	/** Generate 10 random integers in the range 0..99. */
	public static List<Integer> getRandomIntegers(int currentMediaIndex, int quantity) {
        List<Integer> list = new ArrayList<Integer>();
        int i = 0;
        
        for (i = 0; i < quantity; i++)
            list.add(i, i);
        
        Collections.shuffle(list, new Random());
        
        // move currentMediaIndex to the beginning of the
        // list since it is already playing
        System.out.println(list);
        int index = list.indexOf(currentMediaIndex);
        int temp = list.get(0);
        System.out.println(temp);
        list.add(0, currentMediaIndex);
        list.remove(1);
        list.add(index, temp);
        list.remove(index + 1);
        
        System.out.println(list);

        Log.v("TAG", "generated: " + String.valueOf(list.size()));
        
        return list;
	}
}