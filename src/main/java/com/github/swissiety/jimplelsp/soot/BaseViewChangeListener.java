package com.github.swissiety.jimplelsp.soot;

import de.upb.swt.soot.core.model.SootClass;
import de.upb.swt.soot.core.model.SootMethod;

public class BaseViewChangeListener implements ViewChangeListener {
  @Override
  public void classAdded(SootClass sc) {
    System.out.println("added: " + sc);
  }

  @Override
  public void classChanged(SootClass oldClass, SootClass newClass) {
    System.out.println("changed: " + oldClass + " to " + newClass);
  }

  @Override
  public void classRemoved(SootClass sc) {
    System.out.println("removed: " + sc);
  }

  @Override
  public void methodAdded(SootMethod m) {
    System.out.println("added: " + m);
  }

  @Override
  public void methodChanged(SootMethod oldMethod, SootMethod newMethod) {
    System.out.println("changed: " + oldMethod + " to " + newMethod);
  }

  @Override
  public void methodRemoved(SootMethod m) {
    System.out.println("removed: " + m);
  }
}
