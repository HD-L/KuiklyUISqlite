import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from "@kuikly-open/render";

export class KRSandboxPathModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "KRMyLogModule";

  syncMode(): boolean {
    return true;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    // 分发响应
    switch (method) {
      case 'getDatabasesDirectoryPath':
        // 同步返回结果
        return this.getDatabasesDirectoryPath();
    }
    return null
  }

  private getDatabasesDirectoryPath() {
    return getContext().databaseDir;
  }
}
