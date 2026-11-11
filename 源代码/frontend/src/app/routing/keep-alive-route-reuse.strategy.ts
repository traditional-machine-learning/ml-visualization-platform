import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from '@angular/router';

/**
 * 路由保活策略：被登记的路由在切走时只从视图容器上摘下，不销毁组件实例，返回时复用同一个实例。
 *
 * 缓存 experiment（训练中心）的原因：
 *   训练循环由 ExperimentComponent 内部的 setInterval 驱动，离开路由会触发 ngOnDestroy 清理定时器，
 *   再次进入时 ngOnInit 又会重新加载默认案例，导致训练中断且进度被重置。
 *
 * 缓存 learn（学习中心）的原因：
 *   课程浏览状态（当前课节 activeId、三类视图 view、学习进度、随堂自测与理论考核的作答）
 *   全部是组件字段，离开路由销毁组件后就会回到初始课节，返回时无法停留在原来的界面。
 */
export class KeepAliveRouteReuseStrategy implements RouteReuseStrategy {
  /** 需要保活的路由 path，新增页面时在这里登记即可。 */
  private static readonly KEEP_ALIVE_PATHS = new Set<string>(['experiment', 'learn']);

  private readonly handles = new Map<string, DetachedRouteHandle>();

  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    return this.isKeepAlive(route);
  }

  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    if (!this.isKeepAlive(route) || !handle) {
      return;
    }
    this.handles.set(this.keyOf(route), handle);
  }

  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    return this.isKeepAlive(route) && this.handles.has(this.keyOf(route));
  }

  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    if (!this.isKeepAlive(route)) {
      return null;
    }
    return this.handles.get(this.keyOf(route)) ?? null;
  }

  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig;
  }

  private isKeepAlive(route: ActivatedRouteSnapshot): boolean {
    const path = route.routeConfig?.path;
    return !!path && KeepAliveRouteReuseStrategy.KEEP_ALIVE_PATHS.has(path);
  }

  private keyOf(route: ActivatedRouteSnapshot): string {
    return route.routeConfig?.path ?? '';
  }
}
