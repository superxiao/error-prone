/*
 * Copyright 2019 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.canBeRemoved;
import static com.google.errorprone.util.ASTHelpers.enclosingClass;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.shouldKeep;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Bugpattern to detect unused nested classes. */
@BugPattern(
    altNames = "unused",
    summary = "This nested class is unused, and can be removed.",
    severity = WARNING,
    documentSuppression = false)
public final class UnusedNestedClass extends BugChecker implements CompilationUnitTreeMatcher {

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    PrivateNestedClassScanner privateNestedClassScanner = new PrivateNestedClassScanner();
    privateNestedClassScanner.scan(state.getPath(), null);

    Map<ClassSymbol, TreePath> privateNestedClasses = privateNestedClassScanner.classes;

    ClassUsageScanner classUsageScanner = new ClassUsageScanner();
    classUsageScanner.scan(state.getPath(), null);
    privateNestedClasses.keySet().removeAll(classUsageScanner.usedClasses);
    for (TreePath path : privateNestedClasses.values()) {
      state.reportMatch(
          describeMatch(path.getLeaf(), SuggestedFixes.replaceIncludingComments(path, "", state)));
    }
    return NO_MATCH;
  }

  private final class PrivateNestedClassScanner extends TreePathScanner<Void, Void> {
    private final Map<ClassSymbol, TreePath> classes = new HashMap<>();

    private PrivateNestedClassScanner() {}

    @Override
    public Void visitClass(ClassTree classTree, Void unused) {
      if (ignoreUnusedClass(classTree)) {
        return null;
      }
      ClassSymbol symbol = getSymbol(classTree);
      if (symbol != null && canBeRemoved(symbol)) {
        classes.put(symbol, getCurrentPath());
      }
      return super.visitClass(classTree, null);
    }

    private boolean ignoreUnusedClass(ClassTree classTree) {
      return isSuppressed(classTree) || shouldKeep(classTree);
    }
  }

  private static final class ClassUsageScanner extends TreePathScanner<Void, Void> {
    private final Set<ClassSymbol> withinClasses = new HashSet<>();
    private final Set<ClassSymbol> usedClasses = new HashSet<>();

    @Override
    public Void visitClass(ClassTree classTree, Void unused) {
      ClassSymbol symbol = getSymbol(classTree);
      if (symbol == null) {
        return super.visitClass(classTree, null);
      }
      withinClasses.add(symbol);
      super.visitClass(classTree, null);
      withinClasses.remove(symbol);
      return null;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree memberSelectTree, Void unused) {
      handle(memberSelectTree);
      return super.visitMemberSelect(memberSelectTree, null);
    }

    @Override
    public Void visitIdentifier(IdentifierTree identifierTree, Void unused) {
      handle(identifierTree);
      return super.visitIdentifier(identifierTree, null);
    }

    private void handle(Tree node) {
      for (Symbol symbol = getSymbol(node); symbol != null; symbol = enclosingClass(symbol)) {
        if (symbol instanceof ClassSymbol && !withinClasses.contains(symbol)) {
          usedClasses.add((ClassSymbol) symbol);
        }
      }
    }
  }
}
