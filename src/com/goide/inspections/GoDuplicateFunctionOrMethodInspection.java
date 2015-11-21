/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.inspections;

import com.goide.psi.*;
import com.goide.psi.impl.GoPsiImplUtil;
import com.goide.stubs.index.GoFunctionIndex;
import com.goide.stubs.index.GoMethodIndex;
import com.goide.stubs.types.GoMethodDeclarationStubElementType;
import com.goide.util.GoUtil;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.goide.GoConstants.INIT;
import static com.goide.GoConstants.MAIN;

public class GoDuplicateFunctionOrMethodInspection extends GoInspectionBase {
  @NotNull
  @Override
  protected GoVisitor buildGoVisitor(@NotNull final ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    return new GoVisitor() {
      @Override
      public void visitMethodDeclaration(@NotNull final GoMethodDeclaration method) {
        if (method.isBlank()) return;

        final String methodName = method.getName();
        if (methodName == null) return;

        String typeText = GoMethodDeclarationStubElementType.calcTypeText(method);
        if (typeText == null) return;

        GoFile file = method.getContainingFile();
        Project project = file.getProject();
        String packageName = file.getPackageName();
        GlobalSearchScope scope = GoPsiImplUtil.packageScope(file);

        Collection<GoMethodDeclaration> declarations = GoMethodIndex.find(packageName + "." + typeText, project, scope);
        declarations = ContainerUtil.filter(declarations, new Condition<GoMethodDeclaration>() {
          @Override
          public boolean value(GoMethodDeclaration d) {
            return !method.isEquivalentTo(d) && Comparing.equal(d.getName(), methodName) && GoUtil.allowed(d.getContainingFile());
          }
        });

        if (declarations.isEmpty()) return;

        PsiElement identifier = method.getNameIdentifier();
        holder.registerProblem(identifier == null ? method : identifier, "Duplicate method name");
      }

      @Override
      public void visitFunctionDeclaration(@NotNull final GoFunctionDeclaration func) {
        if (func.isBlank()) return;

        final String funcName = func.getName();
        if (funcName == null) return;
        if (INIT.equals(funcName) && zeroArity(func)) return;

        GoFile file = func.getContainingFile();
        Project project = file.getProject();
        GlobalSearchScope scope = GoPsiImplUtil.packageScope(file);

        Collection<GoFunctionDeclaration> declarations = GoFunctionIndex.find(funcName, project, scope);
        Condition<GoFunctionDeclaration> filter;

        if ((MAIN.equals(funcName) && MAIN.equals(func.getContainingFile().getPackageName()) && zeroArity(func))) {
          filter = new Condition<GoFunctionDeclaration>() {
            @Override
            public boolean value(GoFunctionDeclaration d) {
              return !func.isEquivalentTo(d) && Comparing.equal(d.getContainingFile(), func.getContainingFile());
            }
          };
        }
        else {
          filter = new Condition<GoFunctionDeclaration>() {
            @Override
            public boolean value(GoFunctionDeclaration d) {
              return !func.isEquivalentTo(d);
            }
          };
        }

        declarations = ContainerUtil.filter(declarations, filter);

        if (declarations.isEmpty()) return;

        PsiElement identifier = func.getNameIdentifier();
        holder.registerProblem(identifier == null ? func : identifier, "Duplicate function name");
      }
    };
  }

  private static boolean zeroArity(@NotNull GoFunctionDeclaration o) {
    GoSignature signature = o.getSignature();
    return signature == null || signature.getParameters().getParameterDeclarationList().isEmpty();
  }
}
