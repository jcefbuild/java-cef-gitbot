package com.github.smac89.gitbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Hello {
   private static final int ROWS = 10;
   private static final int COLUMNS = 10;

   public static void main(String[] args) {

      ArrayList<ArrayList<Integer>> grid1 = Stream.generate(() -> new ArrayList<>(Collections.nCopies(COLUMNS, 0)))
                                        .limit(ROWS)
                                        .collect(Collectors.toCollection(ArrayList::new));

   }
}
