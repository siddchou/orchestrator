import {a}from'./chunk-BoDIQJU5.js';import {b as bi,y as yi$1,j}from'./chunk-B7REyOXf.js';import {T,a5 as cb,a6 as Mt,a7 as G,a8 as ce,a9 as Er,X as XI,G as Go,aa as db,ab,ac as ha,ad as rb,ae as ob,af as Aa$1,ag as Kd,ah as Xd,ai as lb,aj as Gd,D as D_,y as y_,Q as Ql,j as j_,U as U_,a as Xt$1,C as CE,m as bE,q,f as fi,v as vD,S as Sc,i as Np,ak as uI,N as NE,a2 as ME,O as Op,e as Tv,K as Kp,h as Mp,al as fI,g as SE,u as tE,H as Hl,w as me,am as _i,W as S,p as ee,an as ae,ao as Ws,ap as St,aq as Cm,Y as kF,ar as qE,as as Dp,at as _D,a0 as _p,au as jp,av as zE,aw as QE,ax as Fp,ay as cr,az as hr,B as AF,aA as Ce,aB as j$1,aC as bt,aD as Jo,V as Ve,aE as V,F as Kt$1,aF as hg,aG as rg,aH as yl,aI as Hg,aJ as Kb,L as OF,a3 as bp,z as qp,aK as aD,aL as Up,aM as ch,aN as Oo,Z as bu,_ as _u,aO as WE,a4 as YE,aP as Rr,aQ as sn,aR as uo,aS as Rp,aT as oE,aU as Um,aV as dp,aW as vp,aX as vt,aY as pe,aZ as iI,a_ as Xy,a$ as Jd,d as Ep,P as PE,n as du,$ as $E,o as fu,b0 as Ct,b1 as Uh,r as Nh,b2 as mg,b3 as fr,b4 as yt,b5 as gg,b6 as M,b7 as pg,b8 as kn,b9 as ni,ba as mt$1,bb as Zb,R as Rc,k as kc,bc as wD,bd as th,be as DD,bf as eh,A as Oc,bg as _E}from'./main-BBIEZL5C.js';import {m}from'./chunk-DothdYLc.js';import {Y as Yt$1,m as mt}from'./chunk-CyQ59Z2a.js';import {n}from'./chunk-DNg5_RXp.js';import {G as Ge,Y as Ye$1,U as Ue,v,s as se,W as We,q as qe,Q as Qe$1}from'./chunk-CEuogh1I.js';import {I,K as Ke$1,G as Ge$1}from'./chunk-B5XGMGzP.js';import {Z as Zt,U as Ut,Q as Qt,K as Kt,W as Wt,V as Vt,X as Xt,$ as $t,q as qt,G as Gt,Y as Yt}from'./chunk-BJwr1rdK.js';import {C as Ci,Q as Qe,y as yi,x as xi,n as nn,t as tn,B as Be}from'./chunk-BDw0HWCC.js';var at=["*"];function Fn(a,o){a&1&&WE(0);}var Nn=["tabListContainer"],jn=["tabList"],Vn=["tabListInner"],On=["nextPaginator"],zn=["previousPaginator"],Jn=["content"];function Hn(a,o){}var Gn=["tabBodyWrapper"],Qn=["tabHeader"];function Wn(a,o){}function qn(a,o){if(a&1&&Ep(0,Wn,0,0,"ng-template",12),a&2){let e=$E().$implicit;Mp("cdkPortalOutlet",e.templateLabel);}}function $n(a,o){if(a&1&&vD(0),a&2){let e=$E().$implicit;Kp(e.textLabel);}}function Un(a,o){if(a&1){let e=PE();fi(0,"div",7,2),Op("click",function(){let t=du(e),i=t.$implicit,m=t.$index,A=$E(),j=YE(1);return fu(A._handleClick(i,j,m))})("cdkFocusChange",function(t){let i=du(e).$index,m=$E();return fu(m._tabFocusChanged(t,i))}),Np(2,"span",8)(3,"div",9),fi(4,"span",10)(5,"span",11),CE(6,qn,1,1,null,12)(7,$n,1,1),Sc()()();}if(a&2){let e=o.$implicit,n=o.$index,t=YE(1),i=$E();aD(e.labelClass),qp("mdc-tab--active",i.selectedIndex===n),Mp("id",i._getTabLabelId(e,n))("disabled",e.disabled)("fitInkBarToContent",i.fitInkBarToContent),_p("tabIndex",i._getTabIndex(n))("aria-posinset",n+1)("aria-setsize",i._tabs.length)("aria-controls",i._getTabContentId(n))("aria-selected",i.selectedIndex===n)("aria-label",e.ariaLabel||null)("aria-labelledby",!e.ariaLabel&&e.ariaLabelledby?e.ariaLabelledby:null),Tv(3),Mp("matRippleTrigger",t)("matRippleDisabled",e.disabled||i.disableRipple),Tv(3),bE(e.templateLabel?6:7);}}function Zn(a,o){a&1&&WE(0);}function Kn(a,o){if(a&1){let e=PE();fi(0,"mat-tab-body",13),Op("_onCentered",function(){du(e);let t=$E();return fu(t._removeTabBodyWrapperHeight())})("_onCentering",function(t){du(e);let i=$E();return fu(i._setTabBodyWrapperHeight(t))})("_beforeCentering",function(t){du(e);let i=$E();return fu(i._bodyCentered(t))}),Sc();}if(a&2){let e=o.$implicit,n=o.$index,t=$E();aD(e.bodyClass),Mp("id",t._getTabContentId(n))("content",e.content)("position",e.position)("animationDuration",t._bodyAnimationDuration)("preserveContent",t.preserveContent),_p("tabindex",t.contentTabIndex!=null&&t.selectedIndex===n?t.contentTabIndex:null)("aria-labelledby",t._getTabLabelId(e,n))("aria-hidden",t.selectedIndex!==n);}}var Xn=new S("MatTabContent"),Yn=(()=>{class a{template=T(cr);static \u0275fac=function(n){return new(n||a)};static \u0275dir=oE({type:a,selectors:[["","matTabContent",""]],features:[_D([{provide:Xn,useExisting:a}])]})}return a})(),ea=new S("MatTabLabel"),En=new S("MAT_TAB"),ta=(()=>{class a extends Zb{_closestTab=T(En,{optional:true});static \u0275fac=(()=>{let e;return function(t){return (e||(e=Um(a)))(t||a)}})();static \u0275dir=oE({type:a,selectors:[["","mat-tab-label",""],["","matTabLabel",""]],features:[_D([{provide:ea,useExisting:a}]),vp]})}return a})(),Sn=new S("MAT_TAB_GROUP"),it=(()=>{class a{_viewContainerRef=T(_i);_closestTabGroup=T(Sn,{optional:true});disabled=false;get templateLabel(){return this._templateLabel}set templateLabel(e){this._setTemplateLabelInput(e);}_templateLabel;_explicitContent=void 0;_implicitContent;textLabel="";ariaLabel;ariaLabelledby;labelClass;bodyClass;id=null;_contentPortal=null;get content(){return this._contentPortal}_stateChanges=new ee;position=null;origin=null;isActive=false;constructor(){T(ae).load(Ws);}ngOnChanges(e){(e.hasOwnProperty("textLabel")||e.hasOwnProperty("disabled"))&&this._stateChanges.next();}ngOnDestroy(){this._stateChanges.complete();}ngOnInit(){this._contentPortal=new St(this._explicitContent||this._implicitContent,this._viewContainerRef);}_setTemplateLabelInput(e){e&&e._closestTab===this&&(this._templateLabel=e);}static \u0275fac=function(n){return new(n||a)};static \u0275cmp=XI({type:a,selectors:[["mat-tab"]],contentQueries:function(n,t,i){if(n&1&&Fp(i,ta,5)(i,Yn,7,cr),n&2){let m;zE(m=QE())&&(t.templateLabel=m.first),zE(m=QE())&&(t._explicitContent=m.first);}},viewQuery:function(n,t){if(n&1&&jp(cr,7),n&2){let i;zE(i=QE())&&(t._implicitContent=i.first);}},hostAttrs:["hidden",""],hostVars:1,hostBindings:function(n,t){n&2&&_p("id",null);},inputs:{disabled:[2,"disabled","disabled",kF],textLabel:[0,"label","textLabel"],ariaLabel:[0,"aria-label","ariaLabel"],ariaLabelledby:[0,"aria-labelledby","ariaLabelledby"],labelClass:"labelClass",bodyClass:"bodyClass",id:"id"},exportAs:["matTab"],features:[_D([{provide:En,useExisting:a}]),Cm],ngContentSelectors:at,decls:1,vars:0,template:function(n,t){n&1&&(qE(),Dp(0,Fn,1,0,"ng-template"));},encapsulation:2,changeDetection:1})}return a})(),Ke="mdc-tab-indicator--active",In="mdc-tab-indicator--no-transition",Ye=class{_items;_currentItem;constructor(o){this._items=o;}hide(){this._items.forEach(o=>o.deactivateInkBar()),this._currentItem=void 0;}alignToElement(o){let e=this._items.find(t=>t.elementRef.nativeElement===o),n=this._currentItem;if(e!==n&&(n?.deactivateInkBar(),e)){let t=n?.elementRef.nativeElement.getBoundingClientRect?.();e.activateInkBar(t),this._currentItem=e;}}},na=(()=>{class a{_elementRef=T(hr);_inkBarElement=null;_inkBarContentElement=null;_fitToContent=false;get fitInkBarToContent(){return this._fitToContent}set fitInkBarToContent(e){this._fitToContent!==e&&(this._fitToContent=e,this._inkBarElement&&this._appendInkBarElement());}activateInkBar(e){let n=this._elementRef.nativeElement;if(!e||!n.getBoundingClientRect||!this._inkBarContentElement){n.classList.add(Ke);return}let t=n.getBoundingClientRect(),i=e.width/t.width,m=e.left-t.left;n.classList.add(In),this._inkBarContentElement.style.setProperty("transform",`translateX(${m}px) scaleX(${i})`),n.getBoundingClientRect(),n.classList.remove(In),n.classList.add(Ke),this._inkBarContentElement.style.setProperty("transform","");}deactivateInkBar(){this._elementRef.nativeElement.classList.remove(Ke);}ngOnInit(){this._createInkBarElement();}ngOnDestroy(){this._inkBarElement?.remove(),this._inkBarElement=this._inkBarContentElement=null;}_createInkBarElement(){let e=this._elementRef.nativeElement.ownerDocument||document,n=this._inkBarElement=e.createElement("span"),t=this._inkBarContentElement=e.createElement("span");n.className="mdc-tab-indicator",t.className="mdc-tab-indicator__content mdc-tab-indicator__content--underline",n.appendChild(this._inkBarContentElement),this._appendInkBarElement();}_appendInkBarElement(){this._inkBarElement;let e=this._fitToContent?this._elementRef.nativeElement.querySelector(".mdc-tab__content"):this._elementRef.nativeElement;e.appendChild(this._inkBarElement);}static \u0275fac=function(n){return new(n||a)};static \u0275dir=oE({type:a,inputs:{fitInkBarToContent:[2,"fitInkBarToContent","fitInkBarToContent",kF]}})}return a})();var Mn=(()=>{class a extends na{elementRef=T(hr);disabled=false;focus(){this.elementRef.nativeElement.focus();}getOffsetLeft(){return this.elementRef.nativeElement.offsetLeft}getOffsetWidth(){return this.elementRef.nativeElement.offsetWidth}static \u0275fac=(()=>{let e;return function(t){return (e||(e=Um(a)))(t||a)}})();static \u0275dir=oE({type:a,selectors:[["","matTabLabelWrapper",""]],hostVars:3,hostBindings:function(n,t){n&2&&(_p("aria-disabled",!!t.disabled),qp("mat-mdc-tab-disabled",t.disabled));},inputs:{disabled:[2,"disabled","disabled",kF]},features:[vp]})}return a})(),wn={passive:true},aa=650,ia=100,oa=(()=>{class a{_elementRef=T(hr);_changeDetectorRef=T(AF);_viewportRuler=T(Ct);_dir=T(vt,{optional:true});_ngZone=T(Ce);_platform=T(V);_sharedResizeObserver=T(Be);_injector=T(pe);_renderer=T(iI);_animationsDisabled=bt();_eventCleanups;_scrollDistance=0;_selectedIndexChanged=false;_destroyed=new ee;_showPaginationControls=false;_disableScrollAfter=true;_disableScrollBefore=true;_tabLabelCount;_scrollDistanceChanged=false;_keyManager;_currentTextContent;_stopScrolling=new ee;disablePagination=false;get selectedIndex(){return this._selectedIndex}set selectedIndex(e){let n=isNaN(e)?0:e;this._selectedIndex!=n&&(this._selectedIndexChanged=true,this._selectedIndex=n,this._keyManager&&this._keyManager.updateActiveItem(n));}_selectedIndex=0;selectFocusedIndex=new Ve;indexFocused=new Ve;constructor(){this._eventCleanups=this._ngZone.runOutsideAngular(()=>[this._renderer.listen(this._elementRef.nativeElement,"mouseleave",()=>this._stopInterval())]);}ngAfterViewInit(){this._eventCleanups.push(this._renderer.listen(this._previousPaginator.nativeElement,"touchstart",()=>this._handlePaginatorPress("before"),wn),this._renderer.listen(this._nextPaginator.nativeElement,"touchstart",()=>this._handlePaginatorPress("after"),wn));}ngAfterContentInit(){let e=this._dir?this._dir.change:Uh("ltr"),n=this._sharedResizeObserver.observe(this._elementRef.nativeElement).pipe(Nh(32),mg(this._destroyed)),t=this._viewportRuler.change(150).pipe(mg(this._destroyed)),i=()=>{this.updatePagination(),this._alignInkBarToSelectedTab();};this._keyManager=new fr(this._items).withHorizontalOrientation(this._getLayoutDirection()).withHomeAndEnd().withWrap().skipPredicate(()=>false),this._keyManager.updateActiveItem(Math.max(this._selectedIndex,0)),Xy(i,{injector:this._injector}),rg(e,t,n,this._items.changes,this._itemsResized()).pipe(mg(this._destroyed)).subscribe(()=>{this._ngZone.run(()=>{Promise.resolve().then(()=>{this._scrollDistance=Math.max(0,Math.min(this._getMaxScrollDistance(),this._scrollDistance)),i();});}),this._keyManager?.withHorizontalOrientation(this._getLayoutDirection());}),this._keyManager.change.subscribe(m=>{this.indexFocused.emit(m),this._setTabFocus(m);});}_itemsResized(){return typeof ResizeObserver!="function"?yt:this._items.changes.pipe(hg(this._items),gg(e=>new M(n=>this._ngZone.runOutsideAngular(()=>{let t=new ResizeObserver(i=>n.next(i));return e.forEach(i=>t.observe(i.elementRef.nativeElement)),()=>{t.disconnect();}}))),pg(1),kn(e=>e.some(n=>n.contentRect.width>0&&n.contentRect.height>0)))}ngAfterContentChecked(){this._tabLabelCount!=this._items.length&&(this.updatePagination(),this._tabLabelCount=this._items.length,this._changeDetectorRef.markForCheck()),this._selectedIndexChanged&&(this._scrollToLabel(this._selectedIndex),this._checkScrollingControls(),this._alignInkBarToSelectedTab(),this._selectedIndexChanged=false,this._changeDetectorRef.markForCheck()),this._scrollDistanceChanged&&(this._updateTabScrollPosition(),this._scrollDistanceChanged=false,this._changeDetectorRef.markForCheck());}ngOnDestroy(){this._eventCleanups.forEach(e=>e()),this._keyManager?.destroy(),this._destroyed.next(),this._destroyed.complete(),this._stopScrolling.complete();}_handleKeydown(e){if(!ni(e))switch(e.keyCode){case 13:case 32:if(this.focusIndex!==this.selectedIndex){let n=this._items.get(this.focusIndex);n&&!n.disabled&&(this.selectFocusedIndex.emit(this.focusIndex),this._itemSelected(e));}break;default:this._keyManager?.onKeydown(e);}}_onContentChanges(){let e=this._elementRef.nativeElement.textContent;e!==this._currentTextContent&&(this._currentTextContent=e||"",this._ngZone.run(()=>{this.updatePagination(),this._alignInkBarToSelectedTab(),this._changeDetectorRef.markForCheck();}));}updatePagination(){this._checkPaginationEnabled(),this._checkScrollingControls(),this._updateTabScrollPosition();}get focusIndex(){return this._keyManager?this._keyManager.activeItemIndex:0}set focusIndex(e){!this._isValidIndex(e)||this.focusIndex===e||!this._keyManager||this._keyManager.setActiveItem(e);}_isValidIndex(e){return this._items?!!this._items.toArray()[e]:true}_setTabFocus(e){if(this._showPaginationControls&&this._scrollToLabel(e),this._items&&this._items.length){this._items.toArray()[e].focus();let n=this._tabListContainer.nativeElement;this._getLayoutDirection()=="ltr"?n.scrollLeft=0:n.scrollLeft=n.scrollWidth-n.offsetWidth;}}_getLayoutDirection(){return this._dir&&this._dir.value==="rtl"?"rtl":"ltr"}_updateTabScrollPosition(){if(this.disablePagination)return;let e=this.scrollDistance,n=this._getLayoutDirection()==="ltr"?-e:e;this._tabList.nativeElement.style.transform=`translateX(${Math.round(n)}px)`,(this._platform.TRIDENT||this._platform.EDGE)&&(this._tabListContainer.nativeElement.scrollLeft=0);}get scrollDistance(){return this._scrollDistance}set scrollDistance(e){this._scrollTo(e);}_scrollHeader(e){let n=this._tabListContainer.nativeElement.offsetWidth,t=(e=="before"?-1:1)*n/3;return this._scrollTo(this._scrollDistance+t)}_handlePaginatorClick(e){this._stopInterval(),this._scrollHeader(e);}_scrollToLabel(e){if(this.disablePagination)return;let n=this._items?this._items.toArray()[e]:null;if(!n)return;let t=this._tabListContainer.nativeElement.offsetWidth,{offsetLeft:i,offsetWidth:m}=n.elementRef.nativeElement,A,j;this._getLayoutDirection()=="ltr"?(A=i,j=A+m):(j=this._tabListInner.nativeElement.offsetWidth-i,A=j-m);let Je=this.scrollDistance,st=this.scrollDistance+t;A<Je?this.scrollDistance-=Je-A:j>st&&(this.scrollDistance+=Math.min(j-st,A-Je));}_checkPaginationEnabled(){if(this.disablePagination)this._showPaginationControls=false;else {let e=this._tabListInner.nativeElement.scrollWidth,n=this._elementRef.nativeElement.offsetWidth,t=e-n>=5;t||(this.scrollDistance=0),t!==this._showPaginationControls&&(this._showPaginationControls=t,this._changeDetectorRef.markForCheck());}}_checkScrollingControls(){this.disablePagination?this._disableScrollAfter=this._disableScrollBefore=true:(this._disableScrollBefore=this.scrollDistance==0,this._disableScrollAfter=this.scrollDistance==this._getMaxScrollDistance(),this._changeDetectorRef.markForCheck());}_getMaxScrollDistance(){let e=this._tabListInner.nativeElement.scrollWidth,n=this._tabListContainer.nativeElement.offsetWidth;return e-n||0}_alignInkBarToSelectedTab(){let e=this._items&&this._items.length?this._items.toArray()[this.selectedIndex]:null,n=e?e.elementRef.nativeElement:null;n?this._inkBar.alignToElement(n):this._inkBar.hide();}_stopInterval(){this._stopScrolling.next();}_handlePaginatorPress(e,n){n&&n.button!=null&&n.button!==0||(this._stopInterval(),mt$1(aa,ia).pipe(mg(rg(this._stopScrolling,this._destroyed))).subscribe(()=>{let{maxScrollDistance:t,distance:i}=this._scrollHeader(e);(i===0||i>=t)&&this._stopInterval();}));}_scrollTo(e){if(this.disablePagination)return {maxScrollDistance:0,distance:0};let n=this._getMaxScrollDistance();return this._scrollDistance=Math.max(0,Math.min(n,e)),this._scrollDistanceChanged=true,this._checkScrollingControls(),{maxScrollDistance:n,distance:this._scrollDistance}}static \u0275fac=function(n){return new(n||a)};static \u0275dir=oE({type:a,inputs:{disablePagination:[2,"disablePagination","disablePagination",kF],selectedIndex:[2,"selectedIndex","selectedIndex",OF]},outputs:{selectFocusedIndex:"selectFocusedIndex",indexFocused:"indexFocused"}})}return a})(),ra=(()=>{class a extends oa{_items;_tabListContainer;_tabList;_tabListInner;_nextPaginator;_previousPaginator;_inkBar;ariaLabel;ariaLabelledby;disableRipple=false;ngAfterContentInit(){this._inkBar=new Ye(this._items),super.ngAfterContentInit();}_itemSelected(e){e.preventDefault();}static \u0275fac=(()=>{let e;return function(t){return (e||(e=Um(a)))(t||a)}})();static \u0275cmp=XI({type:a,selectors:[["mat-tab-header"]],contentQueries:function(n,t,i){if(n&1&&Fp(i,Mn,4),n&2){let m;zE(m=QE())&&(t._items=m);}},viewQuery:function(n,t){if(n&1&&jp(Nn,7)(jn,7)(Vn,7)(On,5)(zn,5),n&2){let i;zE(i=QE())&&(t._tabListContainer=i.first),zE(i=QE())&&(t._tabList=i.first),zE(i=QE())&&(t._tabListInner=i.first),zE(i=QE())&&(t._nextPaginator=i.first),zE(i=QE())&&(t._previousPaginator=i.first);}},hostAttrs:[1,"mat-mdc-tab-header"],hostVars:4,hostBindings:function(n,t){n&2&&qp("mat-mdc-tab-header-pagination-controls-enabled",t._showPaginationControls)("mat-mdc-tab-header-rtl",t._getLayoutDirection()=="rtl");},inputs:{ariaLabel:[0,"aria-label","ariaLabel"],ariaLabelledby:[0,"aria-labelledby","ariaLabelledby"],disableRipple:[2,"disableRipple","disableRipple",kF]},features:[vp],ngContentSelectors:at,decls:13,vars:10,consts:[["previousPaginator",""],["tabListContainer",""],["tabList",""],["tabListInner",""],["nextPaginator",""],["mat-ripple","",1,"mat-mdc-tab-header-pagination","mat-mdc-tab-header-pagination-before",3,"click","mousedown","touchend","matRippleDisabled"],[1,"mat-mdc-tab-header-pagination-chevron"],[1,"mat-mdc-tab-label-container",3,"keydown"],["role","tablist",1,"mat-mdc-tab-list",3,"cdkObserveContent"],[1,"mat-mdc-tab-labels"],["mat-ripple","",1,"mat-mdc-tab-header-pagination","mat-mdc-tab-header-pagination-after",3,"mousedown","click","touchend","matRippleDisabled"]],template:function(n,t){n&1&&(qE(),fi(0,"div",5,0),Op("click",function(){return t._handlePaginatorClick("before")})("mousedown",function(m){return t._handlePaginatorPress("before",m)})("touchend",function(){return t._stopInterval()}),Np(2,"div",6),Sc(),fi(3,"div",7,1),Op("keydown",function(m){return t._handleKeydown(m)}),fi(5,"div",8,2),Op("cdkObserveContent",function(){return t._onContentChanges()}),fi(7,"div",9,3),WE(9),Sc()()(),fi(10,"div",10,4),Op("mousedown",function(m){return t._handlePaginatorPress("after",m)})("click",function(){return t._handlePaginatorClick("after")})("touchend",function(){return t._stopInterval()}),Np(12,"div",6),Sc()),n&2&&(qp("mat-mdc-tab-header-pagination-disabled",t._disableScrollBefore),Mp("matRippleDisabled",t._disableScrollBefore||t.disableRipple),Tv(3),qp("_mat-animation-noopable",t._animationsDisabled),Tv(2),_p("aria-label",t.ariaLabel||null)("aria-labelledby",t.ariaLabelledby||null),Tv(5),qp("mat-mdc-tab-header-pagination-disabled",t._disableScrollAfter),Mp("matRippleDisabled",t._disableScrollAfter||t.disableRipple));},dependencies:[Hg,dp],styles:[`.mat-mdc-tab-header {
  display: flex;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}

.mdc-tab-indicator .mdc-tab-indicator__content {
  transition-duration: var(--mat-tab-header-animation-duration, 250ms);
}

.mat-mdc-tab-header-pagination {
  -webkit-user-select: none;
  user-select: none;
  position: relative;
  display: none;
  justify-content: center;
  align-items: center;
  min-width: 32px;
  cursor: pointer;
  z-index: 2;
  -webkit-tap-highlight-color: transparent;
  touch-action: none;
  box-sizing: content-box;
  outline: 0;
}
.mat-mdc-tab-header-pagination::-moz-focus-inner {
  border: 0;
}
.mat-mdc-tab-header-pagination .mat-ripple-element {
  opacity: 0.12;
  background-color: var(--mat-tab-inactive-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-header-pagination-controls-enabled .mat-mdc-tab-header-pagination {
  display: flex;
}

.mat-mdc-tab-header-pagination-before,
.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-after {
  padding-left: 4px;
}
.mat-mdc-tab-header-pagination-before .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-after .mat-mdc-tab-header-pagination-chevron {
  transform: rotate(-135deg);
}

.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-before,
.mat-mdc-tab-header-pagination-after {
  padding-right: 4px;
}
.mat-mdc-tab-header-rtl .mat-mdc-tab-header-pagination-before .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-header-pagination-after .mat-mdc-tab-header-pagination-chevron {
  transform: rotate(45deg);
}

.mat-mdc-tab-header-pagination-chevron {
  border-style: solid;
  border-width: 2px 2px 0 0;
  height: 8px;
  width: 8px;
  border-color: var(--mat-tab-pagination-icon-color, var(--mat-sys-on-surface));
}

.mat-mdc-tab-header-pagination-disabled {
  box-shadow: none;
  cursor: default;
  pointer-events: none;
}
.mat-mdc-tab-header-pagination-disabled .mat-mdc-tab-header-pagination-chevron {
  opacity: 0.4;
}

.mat-mdc-tab-list {
  flex-grow: 1;
  position: relative;
  transition: transform 500ms cubic-bezier(0.35, 0, 0.25, 1);
}
._mat-animation-noopable .mat-mdc-tab-list {
  transition: none;
}

.mat-mdc-tab-label-container {
  display: flex;
  flex-grow: 1;
  overflow: hidden;
  z-index: 1;
  border-bottom-style: solid;
  border-bottom-width: var(--mat-tab-divider-height, 1px);
  border-bottom-color: var(--mat-tab-divider-color, var(--mat-sys-surface-variant));
}
.mat-mdc-tab-group-inverted-header .mat-mdc-tab-label-container {
  border-bottom: none;
  border-top-style: solid;
  border-top-width: var(--mat-tab-divider-height, 1px);
  border-top-color: var(--mat-tab-divider-color, var(--mat-sys-surface-variant));
}

.mat-mdc-tab-labels {
  display: flex;
  flex: 1 0 auto;
}
[mat-align-tabs=center] > .mat-mdc-tab-header .mat-mdc-tab-labels {
  justify-content: center;
}
[mat-align-tabs=end] > .mat-mdc-tab-header .mat-mdc-tab-labels {
  justify-content: flex-end;
}
.cdk-drop-list .mat-mdc-tab-labels, .mat-mdc-tab-labels.cdk-drop-list {
  min-height: var(--mat-tab-container-height, 48px);
}

.mat-mdc-tab::before {
  margin: 5px;
}
@media (forced-colors: active) {
  .mat-mdc-tab[aria-disabled=true] {
    color: GrayText;
  }
}
`],encapsulation:2,changeDetection:1})}return a})(),ca=new S("MAT_TABS_CONFIG"),Dn=(()=>{class a extends Kb{_host=T(et);_ngZone=T(Ce);_centeringSub=j$1.EMPTY;_leavingSub=j$1.EMPTY;ngOnInit(){super.ngOnInit(),this._centeringSub=this._host._beforeCentering.pipe(hg(this._host._isCenterPosition())).subscribe(e=>{this._host._content&&e&&!this.hasAttached()&&this._ngZone.run(()=>{Promise.resolve().then(),this.attach(this._host._content);});}),this._leavingSub=this._host._afterLeavingCenter.subscribe(()=>{this._host.preserveContent||this._ngZone.run(()=>this.detach());});}ngOnDestroy(){super.ngOnDestroy(),this._centeringSub.unsubscribe(),this._leavingSub.unsubscribe();}static \u0275fac=(()=>{let e;return function(t){return (e||(e=Um(a)))(t||a)}})();static \u0275dir=oE({type:a,selectors:[["","matTabBodyHost",""]],features:[vp]})}return a})(),et=(()=>{class a{_elementRef=T(hr);_dir=T(vt,{optional:true});_ngZone=T(Ce);_injector=T(pe);_renderer=T(iI);_diAnimationsDisabled=bt();_eventCleanups;_initialized=false;_fallbackTimer;_positionIndex;_dirChangeSubscription=j$1.EMPTY;_position;_previousPosition;_onCentering=new Ve;_beforeCentering=new Ve;_afterLeavingCenter=new Ve;_onCentered=new Ve(true);_portalHost;_contentElement;_content;animationDuration="500ms";preserveContent=false;set position(e){this._positionIndex=e,this._computePositionAnimationState();}constructor(){if(this._dir){let e=T(AF);this._dirChangeSubscription=this._dir.change.subscribe(n=>{this._computePositionAnimationState(n),e.markForCheck();});}}ngOnInit(){this._bindTransitionEvents(),this._position==="center"&&(this._setActiveClass(true),Xy(()=>this._onCentering.emit(this._elementRef.nativeElement.clientHeight),{injector:this._injector})),this._initialized=true;}ngOnDestroy(){clearTimeout(this._fallbackTimer),this._eventCleanups?.forEach(e=>e()),this._dirChangeSubscription.unsubscribe();}_bindTransitionEvents(){this._ngZone.runOutsideAngular(()=>{let e=this._elementRef.nativeElement,n=t=>{t.target===this._contentElement?.nativeElement&&(this._elementRef.nativeElement.classList.remove("mat-tab-body-animating"),t.type==="transitionend"&&this._transitionDone());};this._eventCleanups=[this._renderer.listen(e,"transitionstart",t=>{t.target===this._contentElement?.nativeElement&&(this._elementRef.nativeElement.classList.add("mat-tab-body-animating"),this._transitionStarted());}),this._renderer.listen(e,"transitionend",n),this._renderer.listen(e,"transitioncancel",n)];});}_transitionStarted(){clearTimeout(this._fallbackTimer);let e=this._position==="center";this._beforeCentering.emit(e),e&&this._onCentering.emit(this._elementRef.nativeElement.clientHeight);}_transitionDone(){this._position==="center"?this._onCentered.emit():this._previousPosition==="center"&&this._afterLeavingCenter.emit();}_setActiveClass(e){this._elementRef.nativeElement.classList.toggle("mat-mdc-tab-body-active",e);}_getLayoutDirection(){return this._dir&&this._dir.value==="rtl"?"rtl":"ltr"}_isCenterPosition(){return this._positionIndex===0}_computePositionAnimationState(e=this._getLayoutDirection()){this._previousPosition=this._position,this._positionIndex<0?this._position=e=="ltr"?"left":"right":this._positionIndex>0?this._position=e=="ltr"?"right":"left":this._position="center",this._animationsDisabled()?this._simulateTransitionEvents():this._initialized&&(this._position==="center"||this._previousPosition==="center")&&(clearTimeout(this._fallbackTimer),this._fallbackTimer=this._ngZone.runOutsideAngular(()=>setTimeout(()=>this._simulateTransitionEvents(),100)));}_simulateTransitionEvents(){this._transitionStarted(),Xy(()=>this._transitionDone(),{injector:this._injector});}_animationsDisabled(){return this._diAnimationsDisabled||this.animationDuration==="0ms"||this.animationDuration==="0s"}static \u0275fac=function(n){return new(n||a)};static \u0275cmp=XI({type:a,selectors:[["mat-tab-body"]],viewQuery:function(n,t){if(n&1&&jp(Dn,5)(Jn,5),n&2){let i;zE(i=QE())&&(t._portalHost=i.first),zE(i=QE())&&(t._contentElement=i.first);}},hostAttrs:[1,"mat-mdc-tab-body"],hostVars:1,hostBindings:function(n,t){n&2&&_p("inert",t._position==="center"?null:"");},inputs:{_content:[0,"content","_content"],animationDuration:"animationDuration",preserveContent:"preserveContent",position:"position"},outputs:{_onCentering:"_onCentering",_beforeCentering:"_beforeCentering",_onCentered:"_onCentered"},decls:3,vars:6,consts:[["content",""],["cdkScrollable","",1,"mat-mdc-tab-body-content"],["matTabBodyHost",""]],template:function(n,t){n&1&&(fi(0,"div",1,0),Ep(2,Hn,0,0,"ng-template",2),Sc()),n&2&&qp("mat-tab-body-content-left",t._position==="left")("mat-tab-body-content-right",t._position==="right")("mat-tab-body-content-can-animate",t._position==="center"||t._previousPosition==="center");},dependencies:[Dn,Jd],styles:[`.mat-mdc-tab-body {
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  position: absolute;
  display: block;
  overflow: hidden;
  outline: 0;
  flex-basis: 100%;
}
.mat-mdc-tab-body.mat-mdc-tab-body-active {
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
  z-index: 1;
  flex-grow: 1;
}
.mat-mdc-tab-group.mat-mdc-tab-group-dynamic-height .mat-mdc-tab-body.mat-mdc-tab-body-active {
  overflow-y: hidden;
}

.mat-mdc-tab-body-content {
  height: 100%;
  overflow: auto;
  transform: none;
  visibility: hidden;
}
.mat-tab-body-animating > .mat-mdc-tab-body-content, .mat-mdc-tab-body-active > .mat-mdc-tab-body-content {
  visibility: visible;
}
.mat-tab-body-animating > .mat-mdc-tab-body-content {
  min-height: 1px;
}
.mat-mdc-tab-group-dynamic-height .mat-mdc-tab-body-content {
  overflow: hidden;
}

.mat-tab-body-content-can-animate {
  transition: transform var(--mat-tab-body-animation-duration) 1ms cubic-bezier(0.35, 0, 0.25, 1);
}
.mat-mdc-tab-body-wrapper._mat-animation-noopable .mat-tab-body-content-can-animate {
  transition: none;
}

.mat-tab-body-content-left {
  transform: translate3d(-100%, 0, 0);
}

.mat-tab-body-content-right {
  transform: translate3d(100%, 0, 0);
}
`],encapsulation:2,changeDetection:1})}return a})(),Rn=(()=>{class a{_elementRef=T(hr);_changeDetectorRef=T(AF);_ngZone=T(Ce);_tabsSubscription=j$1.EMPTY;_tabLabelSubscription=j$1.EMPTY;_tabBodySubscription=j$1.EMPTY;_diAnimationsDisabled=bt();_bodyAnimationDuration;_headerAnimationDuration;_allTabs;_tabBodies;_tabBodyWrapper;_tabHeader;_tabs=new Jo;_indexToSelect=0;_lastFocusedTabIndex=null;_tabBodyWrapperHeight=0;color;get fitInkBarToContent(){return this._fitInkBarToContent}set fitInkBarToContent(e){this._fitInkBarToContent=e,this._changeDetectorRef.markForCheck();}_fitInkBarToContent=false;stretchTabs=true;alignTabs=null;dynamicHeight=false;get selectedIndex(){return this._selectedIndex}set selectedIndex(e){this._indexToSelect=isNaN(e)?null:e;}_selectedIndex=null;headerPosition="above";get animationDuration(){return this._animationDuration}set animationDuration(e){this._animationDuration=e,e&&typeof e=="object"?(this._bodyAnimationDuration=Xe(e.body),this._headerAnimationDuration=Xe(e.header)):this._headerAnimationDuration=this._bodyAnimationDuration=Xe(e);}_animationDuration;get contentTabIndex(){return this._contentTabIndex}set contentTabIndex(e){this._contentTabIndex=isNaN(e)?null:e;}_contentTabIndex=null;disablePagination=false;disableRipple=false;preserveContent=false;get backgroundColor(){return this._backgroundColor}set backgroundColor(e){let n=this._elementRef.nativeElement.classList;n.remove("mat-tabs-with-background",`mat-background-${this.backgroundColor}`),e&&n.add("mat-tabs-with-background",`mat-background-${e}`),this._backgroundColor=e;}_backgroundColor;ariaLabel;ariaLabelledby;selectedIndexChange=new Ve;focusChange=new Ve;animationDone=new Ve;selectedTabChange=new Ve(true);_groupId;_isServer=!T(V).isBrowser;constructor(){let e=T(ca,{optional:true});this._groupId=T(Kt$1).getId("mat-tab-group-"),this.animationDuration=e&&e.animationDuration?e.animationDuration:"500ms",this.disablePagination=e&&e.disablePagination!=null?e.disablePagination:false,this.dynamicHeight=e&&e.dynamicHeight!=null?e.dynamicHeight:false,e?.contentTabIndex!=null&&(this.contentTabIndex=e.contentTabIndex),this.preserveContent=!!e?.preserveContent,this.fitInkBarToContent=e&&e.fitInkBarToContent!=null?e.fitInkBarToContent:false,this.stretchTabs=e&&e.stretchTabs!=null?e.stretchTabs:true,this.alignTabs=e&&e.alignTabs!=null?e.alignTabs:null;}ngAfterContentChecked(){let e=this._indexToSelect=this._clampTabIndex(this._indexToSelect);if(this._selectedIndex!=e){let n=this._selectedIndex==null;if(!n){this.selectedTabChange.emit(this._createChangeEvent(e));let t=this._tabBodyWrapper.nativeElement;t.style.minHeight=t.clientHeight+"px";}Promise.resolve().then(()=>{this._tabs.forEach((t,i)=>t.isActive=i===e),n||(this.selectedIndexChange.emit(e),this._tabBodyWrapper.nativeElement.style.minHeight="");});}this._tabs.forEach((n,t)=>{n.position=t-e,this._selectedIndex!=null&&n.position==0&&!n.origin&&(n.origin=e-this._selectedIndex);}),this._selectedIndex!==e&&(this._selectedIndex=e,this._lastFocusedTabIndex=null,this._changeDetectorRef.markForCheck());}ngAfterContentInit(){this._subscribeToAllTabChanges(),this._subscribeToTabLabels(),this._tabsSubscription=this._tabs.changes.subscribe(()=>{let e=this._clampTabIndex(this._indexToSelect);if(e===this._selectedIndex){let n=this._tabs.toArray(),t;for(let i=0;i<n.length;i++)if(n[i].isActive){this._indexToSelect=this._selectedIndex=i,this._lastFocusedTabIndex=null,t=n[i];break}!t&&n[e]&&Promise.resolve().then(()=>{n[e].isActive=true,this.selectedTabChange.emit(this._createChangeEvent(e));});}this._changeDetectorRef.markForCheck();});}ngAfterViewInit(){this._tabBodySubscription=this._tabBodies.changes.subscribe(()=>this._bodyCentered(true));}_subscribeToAllTabChanges(){this._allTabs.changes.pipe(hg(this._allTabs)).subscribe(e=>{this._tabs.reset(e.filter(n=>n._closestTabGroup===this||!n._closestTabGroup)),this._tabs.notifyOnChanges();});}ngOnDestroy(){this._tabs.destroy(),this._tabsSubscription.unsubscribe(),this._tabLabelSubscription.unsubscribe(),this._tabBodySubscription.unsubscribe();}realignInkBar(){this._tabHeader&&this._tabHeader._alignInkBarToSelectedTab();}updatePagination(){this._tabHeader&&this._tabHeader.updatePagination();}focusTab(e){let n=this._tabHeader;n&&(n.focusIndex=e);}_focusChanged(e){this._lastFocusedTabIndex=e,this.focusChange.emit(this._createChangeEvent(e));}_createChangeEvent(e){let n=new tt;return n.index=e,this._tabs&&this._tabs.length&&(n.tab=this._tabs.toArray()[e]),n}_subscribeToTabLabels(){this._tabLabelSubscription&&this._tabLabelSubscription.unsubscribe(),this._tabLabelSubscription=rg(...this._tabs.map(e=>e._stateChanges)).subscribe(()=>this._changeDetectorRef.markForCheck());}_clampTabIndex(e){return Math.min(this._tabs.length-1,Math.max(e||0,0))}_getTabLabelId(e,n){return e.id||`${this._groupId}-label-${n}`}_getTabContentId(e){return `${this._groupId}-content-${e}`}_setTabBodyWrapperHeight(e){if(!this.dynamicHeight||!this._tabBodyWrapperHeight){this._tabBodyWrapperHeight=e;return}let n=this._tabBodyWrapper.nativeElement;n.style.height=this._tabBodyWrapperHeight+"px",this._tabBodyWrapper.nativeElement.offsetHeight&&(n.style.height=e+"px");}_removeTabBodyWrapperHeight(){let e=this._tabBodyWrapper.nativeElement;this._tabBodyWrapperHeight=e.clientHeight,e.style.height="",this._ngZone.run(()=>this.animationDone.emit());}_handleClick(e,n,t){n.focusIndex=t,e.disabled||(this.selectedIndex=t);}_getTabIndex(e){let n=this._lastFocusedTabIndex??this.selectedIndex;return e===n?0:-1}_tabFocusChanged(e,n){e&&e!=="mouse"&&e!=="touch"&&(this._tabHeader.focusIndex=n);}_bodyCentered(e){e&&this._tabBodies?.forEach((n,t)=>n._setActiveClass(t===this._selectedIndex));}_bodyAnimationsDisabled(){return this._diAnimationsDisabled||this._bodyAnimationDuration==="0"||this._bodyAnimationDuration==="0ms"}static \u0275fac=function(n){return new(n||a)};static \u0275cmp=XI({type:a,selectors:[["mat-tab-group"]],contentQueries:function(n,t,i){if(n&1&&Fp(i,it,5),n&2){let m;zE(m=QE())&&(t._allTabs=m);}},viewQuery:function(n,t){if(n&1&&jp(Gn,5)(Qn,5)(et,5),n&2){let i;zE(i=QE())&&(t._tabBodyWrapper=i.first),zE(i=QE())&&(t._tabHeader=i.first),zE(i=QE())&&(t._tabBodies=i);}},hostAttrs:[1,"mat-mdc-tab-group"],hostVars:13,hostBindings:function(n,t){n&2&&(_p("mat-align-tabs",t.alignTabs),aD("mat-"+(t.color||"primary")),Up("--mat-tab-body-animation-duration",t._bodyAnimationDuration)("--mat-tab-header-animation-duration",t._headerAnimationDuration),qp("mat-mdc-tab-group-dynamic-height",t.dynamicHeight)("mat-mdc-tab-group-inverted-header",t.headerPosition==="below")("mat-mdc-tab-group-stretch-tabs",t.stretchTabs));},inputs:{color:"color",fitInkBarToContent:[2,"fitInkBarToContent","fitInkBarToContent",kF],stretchTabs:[2,"mat-stretch-tabs","stretchTabs",kF],alignTabs:[0,"mat-align-tabs","alignTabs"],dynamicHeight:[2,"dynamicHeight","dynamicHeight",kF],selectedIndex:[2,"selectedIndex","selectedIndex",OF],headerPosition:"headerPosition",animationDuration:"animationDuration",contentTabIndex:[2,"contentTabIndex","contentTabIndex",OF],disablePagination:[2,"disablePagination","disablePagination",kF],disableRipple:[2,"disableRipple","disableRipple",kF],preserveContent:[2,"preserveContent","preserveContent",kF],backgroundColor:"backgroundColor",ariaLabel:[0,"aria-label","ariaLabel"],ariaLabelledby:[0,"aria-labelledby","ariaLabelledby"]},outputs:{selectedIndexChange:"selectedIndexChange",focusChange:"focusChange",animationDone:"animationDone",selectedTabChange:"selectedTabChange"},exportAs:["matTabGroup"],features:[_D([{provide:Sn,useExisting:a}])],ngContentSelectors:at,decls:9,vars:8,consts:[["tabHeader",""],["tabBodyWrapper",""],["tabNode",""],[3,"indexFocused","selectFocusedIndex","selectedIndex","disableRipple","disablePagination","aria-label","aria-labelledby"],["role","tab","matTabLabelWrapper","","cdkMonitorElementFocus","",1,"mdc-tab","mat-mdc-tab","mat-focus-indicator",3,"id","mdc-tab--active","class","disabled","fitInkBarToContent"],[1,"mat-mdc-tab-body-wrapper"],["role","tabpanel",3,"id","class","content","position","animationDuration","preserveContent"],["role","tab","matTabLabelWrapper","","cdkMonitorElementFocus","",1,"mdc-tab","mat-mdc-tab","mat-focus-indicator",3,"click","cdkFocusChange","id","disabled","fitInkBarToContent"],[1,"mdc-tab__ripple"],["mat-ripple","",1,"mat-mdc-tab-ripple",3,"matRippleTrigger","matRippleDisabled"],[1,"mdc-tab__content"],[1,"mdc-tab__text-label"],[3,"cdkPortalOutlet"],["role","tabpanel",3,"_onCentered","_onCentering","_beforeCentering","id","content","position","animationDuration","preserveContent"]],template:function(n,t){n&1&&(qE(),fi(0,"mat-tab-header",3,0),Op("indexFocused",function(m){return t._focusChanged(m)})("selectFocusedIndex",function(m){return t.selectedIndex=m}),NE(2,Un,8,17,"div",4,ME),Sc(),CE(4,Zn,1,0),fi(5,"div",5,1),NE(7,Kn,1,10,"mat-tab-body",6,ME),Sc()),n&2&&(Mp("selectedIndex",t.selectedIndex||0)("disableRipple",t.disableRipple)("disablePagination",t.disablePagination),bp("aria-label",t.ariaLabel)("aria-labelledby",t.ariaLabelledby),Tv(2),SE(t._tabs),Tv(2),bE(t._isServer?4:-1),Tv(),qp("_mat-animation-noopable",t._bodyAnimationsDisabled()),Tv(2),SE(t._tabs));},dependencies:[ra,Mn,yl,Hg,Kb,et],styles:[`.mdc-tab {
  min-width: 90px;
  padding: 0 24px;
  display: flex;
  flex: 1 0 auto;
  justify-content: center;
  box-sizing: border-box;
  border: none;
  outline: none;
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
  z-index: 1;
  touch-action: manipulation;
}

.mdc-tab__content {
  display: flex;
  align-items: center;
  justify-content: center;
  height: inherit;
  pointer-events: none;
}

.mdc-tab__text-label {
  transition: 150ms color linear;
  display: inline-block;
  line-height: 1;
  z-index: 2;
}

.mdc-tab--active .mdc-tab__text-label {
  transition-delay: 100ms;
}

._mat-animation-noopable .mdc-tab__text-label {
  transition: none;
}

.mdc-tab-indicator {
  display: flex;
  position: absolute;
  top: 0;
  left: 0;
  justify-content: center;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.mdc-tab-indicator__content {
  transition: var(--mat-tab-header-animation-duration, 250ms) transform cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: left;
  opacity: 0;
}

.mdc-tab-indicator__content--underline {
  align-self: flex-end;
  box-sizing: border-box;
  width: 100%;
  border-top-style: solid;
}

.mdc-tab-indicator--active .mdc-tab-indicator__content {
  opacity: 1;
}

._mat-animation-noopable .mdc-tab-indicator__content, .mdc-tab-indicator--no-transition .mdc-tab-indicator__content {
  transition: none;
}

.mat-mdc-tab-ripple.mat-mdc-tab-ripple {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  right: 0;
  pointer-events: none;
}

.mat-mdc-tab {
  -webkit-tap-highlight-color: transparent;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-decoration: none;
  background: none;
  height: var(--mat-tab-container-height, 48px);
  font-family: var(--mat-tab-label-text-font, var(--mat-sys-title-small-font));
  font-size: var(--mat-tab-label-text-size, var(--mat-sys-title-small-size));
  letter-spacing: var(--mat-tab-label-text-tracking, var(--mat-sys-title-small-tracking));
  line-height: var(--mat-tab-label-text-line-height, var(--mat-sys-title-small-line-height));
  font-weight: var(--mat-tab-label-text-weight, var(--mat-sys-title-small-weight));
}
.mat-mdc-tab.mdc-tab {
  flex-grow: 0;
}
.mat-mdc-tab .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-active-indicator-color, var(--mat-sys-primary));
  border-top-width: var(--mat-tab-active-indicator-height, 2px);
  border-radius: var(--mat-tab-active-indicator-shape, 0);
}
.mat-mdc-tab:hover .mdc-tab__text-label {
  color: var(--mat-tab-inactive-hover-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab:focus .mdc-tab__text-label {
  color: var(--mat-tab-inactive-focus-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab.mdc-tab--active .mdc-tab__text-label {
  color: var(--mat-tab-active-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab.mdc-tab--active .mdc-tab__ripple::before,
.mat-mdc-tab.mdc-tab--active .mat-ripple-element {
  background-color: var(--mat-tab-active-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab.mdc-tab--active:hover .mdc-tab__text-label {
  color: var(--mat-tab-active-hover-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab.mdc-tab--active:hover .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-active-hover-indicator-color, var(--mat-sys-primary));
}
.mat-mdc-tab.mdc-tab--active:focus .mdc-tab__text-label {
  color: var(--mat-tab-active-focus-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab.mdc-tab--active:focus .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-active-focus-indicator-color, var(--mat-sys-primary));
}
.mat-mdc-tab.mat-mdc-tab-disabled {
  opacity: 0.4;
  pointer-events: none;
}
.mat-mdc-tab.mat-mdc-tab-disabled .mdc-tab__content {
  pointer-events: none;
}
.mat-mdc-tab.mat-mdc-tab-disabled .mdc-tab__ripple::before,
.mat-mdc-tab.mat-mdc-tab-disabled .mat-ripple-element {
  background-color: var(--mat-tab-disabled-ripple-color, var(--mat-sys-on-surface-variant));
}
.mat-mdc-tab .mdc-tab__ripple::before {
  content: "";
  display: block;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0;
  pointer-events: none;
  background-color: var(--mat-tab-inactive-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab .mdc-tab__text-label {
  color: var(--mat-tab-inactive-label-text-color, var(--mat-sys-on-surface));
  display: inline-flex;
  align-items: center;
}
.mat-mdc-tab .mdc-tab__content {
  position: relative;
  pointer-events: auto;
}
.mat-mdc-tab:hover .mdc-tab__ripple::before {
  opacity: 0.04;
}
.mat-mdc-tab.cdk-program-focused .mdc-tab__ripple::before, .mat-mdc-tab.cdk-keyboard-focused .mdc-tab__ripple::before {
  opacity: 0.12;
}
.mat-mdc-tab .mat-ripple-element {
  opacity: 0.12;
  background-color: var(--mat-tab-inactive-ripple-color, var(--mat-sys-on-surface));
}
.mat-mdc-tab-group.mat-mdc-tab-group-stretch-tabs > .mat-mdc-tab-header .mat-mdc-tab {
  flex-grow: 1;
}

.mat-mdc-tab-group {
  display: flex;
  flex-direction: column;
  max-width: 100%;
}
.mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header, .mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header-pagination {
  background-color: var(--mat-tab-background-color);
}
.mat-mdc-tab-group.mat-tabs-with-background.mat-primary > .mat-mdc-tab-header .mat-mdc-tab .mdc-tab__text-label {
  color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-tabs-with-background.mat-primary > .mat-mdc-tab-header .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-tabs-with-background:not(.mat-primary) > .mat-mdc-tab-header .mat-mdc-tab:not(.mdc-tab--active) .mdc-tab__text-label {
  color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-tabs-with-background:not(.mat-primary) > .mat-mdc-tab-header .mat-mdc-tab:not(.mdc-tab--active) .mdc-tab-indicator__content--underline {
  border-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header .mat-focus-indicator::before, .mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-mdc-tab-header-pagination-chevron,
.mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-focus-indicator::before {
  border-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header .mat-ripple-element, .mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header .mdc-tab__ripple::before, .mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-ripple-element, .mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mdc-tab__ripple::before {
  background-color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header .mat-mdc-tab-header-pagination-chevron, .mat-mdc-tab-group.mat-tabs-with-background > .mat-mdc-tab-header-pagination .mat-mdc-tab-header-pagination-chevron {
  color: var(--mat-tab-foreground-color);
}
.mat-mdc-tab-group.mat-mdc-tab-group-inverted-header {
  flex-direction: column-reverse;
}
.mat-mdc-tab-group.mat-mdc-tab-group-inverted-header .mdc-tab-indicator__content--underline {
  align-self: flex-start;
}

.mat-mdc-tab-body-wrapper {
  position: relative;
  overflow: hidden;
  display: flex;
  transition: height 500ms cubic-bezier(0.35, 0, 0.25, 1);
}
.mat-mdc-tab-body-wrapper._mat-animation-noopable {
  transition: none !important;
  animation: none !important;
}
`],encapsulation:2,changeDetection:1})}return a})(),tt=class{index;tab};function Xe(a){let o=a+"";return /^\d+$/.test(o)?a+"ms":o}var Pn=(()=>{class a{static \u0275fac=function(n){return new(n||a)};static \u0275mod=tE({type:a});static \u0275inj=Hl({imports:[me]})}return a})();var la=["input"],da=["label"],ma=["*"],ot={color:"accent",clickAction:"check-indeterminate",disabledInteractive:false},ba=new S("mat-checkbox-default-options",{providedIn:"root",factory:()=>ot}),x=(function(a){return a[a.Init=0]="Init",a[a.Checked=1]="Checked",a[a.Unchecked=2]="Unchecked",a[a.Indeterminate=3]="Indeterminate",a})(x||{}),rt=class{source;checked},ct=(()=>{class a{_elementRef=T(hr);_changeDetectorRef=T(AF);_ngZone=T(Ce);_animationsDisabled=bt();_options=T(ba,{optional:true});focus(){this._inputElement.nativeElement.focus();}_createChangeEvent(e){let n=new rt;return n.source=this,n.checked=e,n}_getAnimationTargetElement(){return this._inputElement?.nativeElement}_animationClasses={uncheckedToChecked:"mdc-checkbox--anim-unchecked-checked",uncheckedToIndeterminate:"mdc-checkbox--anim-unchecked-indeterminate",checkedToUnchecked:"mdc-checkbox--anim-checked-unchecked",checkedToIndeterminate:"mdc-checkbox--anim-checked-indeterminate",indeterminateToChecked:"mdc-checkbox--anim-indeterminate-checked",indeterminateToUnchecked:"mdc-checkbox--anim-indeterminate-unchecked"};ariaLabel="";ariaLabelledby=null;ariaDescribedby;ariaExpanded;ariaControls;ariaOwns;_uniqueId;id;get inputId(){return `${this.id||this._uniqueId}-input`}required=false;labelPosition="after";name=null;change=new Ve;indeterminateChange=new Ve;value;disableRipple=false;_inputElement;_labelElement;tabIndex;color;disabledInteractive;_onTouched=()=>{};_currentAnimationClass="";_currentCheckState=x.Init;_controlValueAccessorChangeFn=()=>{};_validatorChangeFn=()=>{};constructor(){T(ae).load(Ws);let e=T(new ch("tabindex"),{optional:true});this._options=this._options||ot,this.color=this._options.color||ot.color,this.tabIndex=e==null?0:parseInt(e)||0,this.id=this._uniqueId=T(Kt$1).getId("mat-mdc-checkbox-"),this.disabledInteractive=this._options?.disabledInteractive??false;}ngOnChanges(e){e.required&&this._validatorChangeFn();}ngAfterViewInit(){this._syncIndeterminate(this.indeterminate);}get checked(){return this._checked}set checked(e){e!=this.checked&&(this._checked=e,this._changeDetectorRef.markForCheck());}_checked=false;get disabled(){return this._disabled}set disabled(e){e!==this.disabled&&(this._disabled=e,this._changeDetectorRef.markForCheck());}_disabled=false;get indeterminate(){return this._indeterminate()}set indeterminate(e){let n=e!=this._indeterminate();this._indeterminate.set(e),n&&(e?this._transitionCheckState(x.Indeterminate):this._transitionCheckState(this.checked?x.Checked:x.Unchecked),this.indeterminateChange.emit(e)),this._syncIndeterminate(e);}_indeterminate=Oo(false);_isRippleDisabled(){return this.disableRipple||this.disabled}_onLabelTextChange(){this._changeDetectorRef.detectChanges();}writeValue(e){this.checked=!!e;}registerOnChange(e){this._controlValueAccessorChangeFn=e;}registerOnTouched(e){this._onTouched=e;}setDisabledState(e){this.disabled=e;}validate(e){return this.required&&e.value!==true?{required:true}:null}registerOnValidatorChange(e){this._validatorChangeFn=e;}_transitionCheckState(e){let n=this._currentCheckState,t=this._getAnimationTargetElement();if(!(n===e||!t)&&(this._currentAnimationClass&&t.classList.remove(this._currentAnimationClass),this._currentAnimationClass=this._getAnimationClassForCheckStateTransition(n,e),this._currentCheckState=e,this._currentAnimationClass.length>0)){t.classList.add(this._currentAnimationClass);let i=this._currentAnimationClass;this._ngZone.runOutsideAngular(()=>{setTimeout(()=>{t.classList.remove(i);},1e3);});}}_emitChangeEvent(){this._controlValueAccessorChangeFn(this.checked),this.change.emit(this._createChangeEvent(this.checked)),this._inputElement&&(this._inputElement.nativeElement.checked=this.checked);}toggle(){this.checked=!this.checked,this._controlValueAccessorChangeFn(this.checked);}_handleInputClick(){let e=this._options?.clickAction;!this.disabled&&e!=="noop"?(this.indeterminate&&e!=="check"&&Promise.resolve().then(()=>{this._indeterminate.set(false),this.indeterminateChange.emit(false);}),this._checked=!this._checked,this._transitionCheckState(this._checked?x.Checked:x.Unchecked),this._emitChangeEvent()):(this.disabled&&this.disabledInteractive||!this.disabled&&e==="noop")&&(this._inputElement.nativeElement.checked=this.checked,this._inputElement.nativeElement.indeterminate=this.indeterminate);}_onInteractionEvent(e){e.stopPropagation();}_onBlur(){Promise.resolve().then(()=>{this._onTouched(),this._changeDetectorRef.markForCheck();});}_getAnimationClassForCheckStateTransition(e,n){if(this._animationsDisabled)return "";switch(e){case x.Init:if(n===x.Checked)return this._animationClasses.uncheckedToChecked;if(n==x.Indeterminate)return this._checked?this._animationClasses.checkedToIndeterminate:this._animationClasses.uncheckedToIndeterminate;break;case x.Unchecked:return n===x.Checked?this._animationClasses.uncheckedToChecked:this._animationClasses.uncheckedToIndeterminate;case x.Checked:return n===x.Unchecked?this._animationClasses.checkedToUnchecked:this._animationClasses.checkedToIndeterminate;case x.Indeterminate:return n===x.Checked?this._animationClasses.indeterminateToChecked:this._animationClasses.indeterminateToUnchecked}return ""}_syncIndeterminate(e){let n=this._inputElement;n&&(n.nativeElement.indeterminate=e);}_onInputClick(){this._handleInputClick();}_onTouchTargetClick(){this._handleInputClick(),this.disabled||this._inputElement.nativeElement.focus();}_preventBubblingFromLabel(e){e.target&&this._labelElement.nativeElement.contains(e.target)&&e.stopPropagation();}static \u0275fac=function(n){return new(n||a)};static \u0275cmp=XI({type:a,selectors:[["mat-checkbox"]],viewQuery:function(n,t){if(n&1&&jp(la,5)(da,5),n&2){let i;zE(i=QE())&&(t._inputElement=i.first),zE(i=QE())&&(t._labelElement=i.first);}},hostAttrs:[1,"mat-mdc-checkbox"],hostVars:16,hostBindings:function(n,t){n&2&&(Rp("id",t.id),_p("tabindex",null)("aria-label",null)("aria-labelledby",null),aD(t.color?"mat-"+t.color:"mat-accent"),qp("_mat-animation-noopable",t._animationsDisabled)("mdc-checkbox--disabled",t.disabled)("mat-mdc-checkbox-disabled",t.disabled)("mat-mdc-checkbox-checked",t.checked)("mat-mdc-checkbox-disabled-interactive",t.disabledInteractive));},inputs:{ariaLabel:[0,"aria-label","ariaLabel"],ariaLabelledby:[0,"aria-labelledby","ariaLabelledby"],ariaDescribedby:[0,"aria-describedby","ariaDescribedby"],ariaExpanded:[2,"aria-expanded","ariaExpanded",kF],ariaControls:[0,"aria-controls","ariaControls"],ariaOwns:[0,"aria-owns","ariaOwns"],id:"id",required:[2,"required","required",kF],labelPosition:"labelPosition",name:"name",value:"value",disableRipple:[2,"disableRipple","disableRipple",kF],tabIndex:[2,"tabIndex","tabIndex",e=>e==null?void 0:OF(e)],color:"color",disabledInteractive:[2,"disabledInteractive","disabledInteractive",kF],checked:[2,"checked","checked",kF],disabled:[2,"disabled","disabled",kF],indeterminate:[2,"indeterminate","indeterminate",kF]},outputs:{change:"change",indeterminateChange:"indeterminateChange"},exportAs:["matCheckbox"],features:[_D([{provide:Rr,useExisting:uo(()=>a),multi:true},{provide:sn,useExisting:a,multi:true}]),Cm],ngContentSelectors:ma,decls:15,vars:23,consts:[["checkbox",""],["input",""],["label",""],["mat-internal-form-field","",3,"click","labelPosition"],[1,"mdc-checkbox"],["aria-hidden","true",1,"mat-mdc-checkbox-touch-target",3,"click"],["type","checkbox",1,"mdc-checkbox__native-control",3,"blur","click","change","checked","indeterminate","disabled","id","required","tabIndex"],["aria-hidden","true",1,"mdc-checkbox__ripple"],["aria-hidden","true",1,"mdc-checkbox__background"],["focusable","false","viewBox","0 0 24 24",1,"mdc-checkbox__checkmark"],["fill","none","d","M1.73,12.91 8.1,19.28 22.79,4.59",1,"mdc-checkbox__checkmark-path"],[1,"mdc-checkbox__mixedmark"],["mat-ripple","","aria-hidden","true",1,"mat-mdc-checkbox-ripple","mat-focus-indicator",3,"matRippleTrigger","matRippleDisabled","matRippleCentered"],[1,"mdc-label",3,"for"]],template:function(n,t){if(n&1&&(qE(),fi(0,"div",3),Op("click",function(m){return t._preventBubblingFromLabel(m)}),fi(1,"div",4,0)(3,"div",5),Op("click",function(){return t._onTouchTargetClick()}),Sc(),fi(4,"input",6,1),Op("blur",function(){return t._onBlur()})("click",function(){return t._onInputClick()})("change",function(m){return t._onInteractionEvent(m)}),Sc(),Np(6,"div",7),fi(7,"div",8),bu(),fi(8,"svg",9),Np(9,"path",10),Sc(),_u(),Np(10,"div",11),Sc(),Np(11,"div",12),Sc(),fi(12,"label",13,2),WE(14),Sc()()),n&2){let i=YE(2);Mp("labelPosition",t.labelPosition),Tv(4),qp("mdc-checkbox--selected",t.checked),Mp("checked",t.checked)("indeterminate",t.indeterminate)("disabled",t.disabled&&!t.disabledInteractive)("id",t.inputId)("required",t.required)("tabIndex",t.disabled&&!t.disabledInteractive?-1:t.tabIndex),_p("aria-label",t.ariaLabel||null)("aria-labelledby",t.ariaLabelledby)("aria-describedby",t.ariaDescribedby)("aria-checked",t.indeterminate?"mixed":null)("aria-controls",t.ariaControls)("aria-disabled",t.disabled&&t.disabledInteractive?true:null)("aria-expanded",t.ariaExpanded)("aria-owns",t.ariaOwns)("name",t.name)("value",t.value),Tv(7),Mp("matRippleTrigger",i)("matRippleDisabled",t.disableRipple||t.disabled)("matRippleCentered",true),Tv(),Mp("for",t.inputId);}},dependencies:[Hg,m],styles:[`.mdc-checkbox {
  display: inline-block;
  position: relative;
  flex: 0 0 18px;
  box-sizing: content-box;
  width: 18px;
  height: 18px;
  line-height: 0;
  white-space: nowrap;
  cursor: pointer;
  vertical-align: bottom;
  padding: calc((var(--mat-checkbox-state-layer-size, 40px) - 18px) / 2);
  margin: calc((var(--mat-checkbox-state-layer-size, 40px) - var(--mat-checkbox-state-layer-size, 40px)) / 2);
}
.mdc-checkbox:hover > .mdc-checkbox__ripple {
  opacity: var(--mat-checkbox-unselected-hover-state-layer-opacity, var(--mat-sys-hover-state-layer-opacity));
  background-color: var(--mat-checkbox-unselected-hover-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox:hover > .mat-mdc-checkbox-ripple > .mat-ripple-element {
  background-color: var(--mat-checkbox-unselected-hover-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox .mdc-checkbox__native-control:focus + .mdc-checkbox__ripple {
  opacity: var(--mat-checkbox-unselected-focus-state-layer-opacity, var(--mat-sys-focus-state-layer-opacity));
  background-color: var(--mat-checkbox-unselected-focus-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox .mdc-checkbox__native-control:focus ~ .mat-mdc-checkbox-ripple .mat-ripple-element {
  background-color: var(--mat-checkbox-unselected-focus-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox:active > .mdc-checkbox__native-control + .mdc-checkbox__ripple {
  opacity: var(--mat-checkbox-unselected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));
  background-color: var(--mat-checkbox-unselected-pressed-state-layer-color, var(--mat-sys-primary));
}
.mdc-checkbox:active > .mdc-checkbox__native-control ~ .mat-mdc-checkbox-ripple .mat-ripple-element {
  background-color: var(--mat-checkbox-unselected-pressed-state-layer-color, var(--mat-sys-primary));
}
.mdc-checkbox:hover > .mdc-checkbox__native-control:checked + .mdc-checkbox__ripple {
  opacity: var(--mat-checkbox-selected-hover-state-layer-opacity, var(--mat-sys-hover-state-layer-opacity));
  background-color: var(--mat-checkbox-selected-hover-state-layer-color, var(--mat-sys-primary));
}
.mdc-checkbox:hover > .mdc-checkbox__native-control:checked ~ .mat-mdc-checkbox-ripple .mat-ripple-element {
  background-color: var(--mat-checkbox-selected-hover-state-layer-color, var(--mat-sys-primary));
}
.mdc-checkbox .mdc-checkbox__native-control:focus:checked + .mdc-checkbox__ripple {
  opacity: var(--mat-checkbox-selected-focus-state-layer-opacity, var(--mat-sys-focus-state-layer-opacity));
  background-color: var(--mat-checkbox-selected-focus-state-layer-color, var(--mat-sys-primary));
}
.mdc-checkbox .mdc-checkbox__native-control:focus:checked ~ .mat-mdc-checkbox-ripple .mat-ripple-element {
  background-color: var(--mat-checkbox-selected-focus-state-layer-color, var(--mat-sys-primary));
}
.mdc-checkbox:active > .mdc-checkbox__native-control:checked + .mdc-checkbox__ripple {
  opacity: var(--mat-checkbox-selected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));
  background-color: var(--mat-checkbox-selected-pressed-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox:active > .mdc-checkbox__native-control:checked ~ .mat-mdc-checkbox-ripple .mat-ripple-element {
  background-color: var(--mat-checkbox-selected-pressed-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox .mdc-checkbox__native-control ~ .mat-mdc-checkbox-ripple .mat-ripple-element,
.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox .mdc-checkbox__native-control + .mdc-checkbox__ripple {
  background-color: var(--mat-checkbox-unselected-hover-state-layer-color, var(--mat-sys-on-surface));
}
.mdc-checkbox .mdc-checkbox__native-control {
  position: absolute;
  margin: 0;
  padding: 0;
  opacity: 0;
  cursor: inherit;
  z-index: 1;
  width: var(--mat-checkbox-state-layer-size, 40px);
  height: var(--mat-checkbox-state-layer-size, 40px);
  top: calc((var(--mat-checkbox-state-layer-size, 40px) - var(--mat-checkbox-state-layer-size, 40px)) / 2);
  right: calc((var(--mat-checkbox-state-layer-size, 40px) - var(--mat-checkbox-state-layer-size, 40px)) / 2);
  left: calc((var(--mat-checkbox-state-layer-size, 40px) - var(--mat-checkbox-state-layer-size, 40px)) / 2);
}

.mdc-checkbox--disabled {
  cursor: default;
  pointer-events: none;
}

.mdc-checkbox__background {
  display: inline-flex;
  position: absolute;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 18px;
  height: 18px;
  border: 2px solid currentColor;
  border-radius: 2px;
  background-color: transparent;
  pointer-events: none;
  will-change: background-color, border-color;
  transition: background-color 90ms cubic-bezier(0.4, 0, 0.6, 1), border-color 90ms cubic-bezier(0.4, 0, 0.6, 1);
  -webkit-print-color-adjust: exact;
  color-adjust: exact;
  border-color: var(--mat-checkbox-unselected-icon-color, var(--mat-sys-on-surface-variant));
  top: calc((var(--mat-checkbox-state-layer-size, 40px) - 18px) / 2);
  left: calc((var(--mat-checkbox-state-layer-size, 40px) - 18px) / 2);
}

.mdc-checkbox__native-control:enabled:checked ~ .mdc-checkbox__background,
.mdc-checkbox__native-control:enabled:indeterminate ~ .mdc-checkbox__background {
  border-color: var(--mat-checkbox-selected-icon-color, var(--mat-sys-primary));
  background-color: var(--mat-checkbox-selected-icon-color, var(--mat-sys-primary));
}

.mdc-checkbox--disabled .mdc-checkbox__background {
  border-color: var(--mat-checkbox-disabled-unselected-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent));
}
@media (forced-colors: active) {
  .mdc-checkbox--disabled .mdc-checkbox__background {
    border-color: GrayText;
  }
}

.mdc-checkbox__native-control:disabled:checked ~ .mdc-checkbox__background,
.mdc-checkbox__native-control:disabled:indeterminate ~ .mdc-checkbox__background {
  background-color: var(--mat-checkbox-disabled-selected-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent));
  border-color: transparent;
}
@media (forced-colors: active) {
  .mdc-checkbox__native-control:disabled:checked ~ .mdc-checkbox__background,
  .mdc-checkbox__native-control:disabled:indeterminate ~ .mdc-checkbox__background {
    border-color: GrayText;
  }
}

.mdc-checkbox:hover > .mdc-checkbox__native-control:not(:checked) ~ .mdc-checkbox__background,
.mdc-checkbox:hover > .mdc-checkbox__native-control:not(:indeterminate) ~ .mdc-checkbox__background {
  border-color: var(--mat-checkbox-unselected-hover-icon-color, var(--mat-sys-on-surface));
  background-color: transparent;
}

.mdc-checkbox:hover > .mdc-checkbox__native-control:checked ~ .mdc-checkbox__background,
.mdc-checkbox:hover > .mdc-checkbox__native-control:indeterminate ~ .mdc-checkbox__background {
  border-color: var(--mat-checkbox-selected-hover-icon-color, var(--mat-sys-primary));
  background-color: var(--mat-checkbox-selected-hover-icon-color, var(--mat-sys-primary));
}

.mdc-checkbox__native-control:focus:focus:not(:checked) ~ .mdc-checkbox__background,
.mdc-checkbox__native-control:focus:focus:not(:indeterminate) ~ .mdc-checkbox__background {
  border-color: var(--mat-checkbox-unselected-focus-icon-color, var(--mat-sys-on-surface));
}

.mdc-checkbox__native-control:focus:focus:checked ~ .mdc-checkbox__background,
.mdc-checkbox__native-control:focus:focus:indeterminate ~ .mdc-checkbox__background {
  border-color: var(--mat-checkbox-selected-focus-icon-color, var(--mat-sys-primary));
  background-color: var(--mat-checkbox-selected-focus-icon-color, var(--mat-sys-primary));
}

.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox:hover > .mdc-checkbox__native-control ~ .mdc-checkbox__background,
.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox .mdc-checkbox__native-control:focus ~ .mdc-checkbox__background,
.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__background {
  border-color: var(--mat-checkbox-disabled-unselected-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent));
}
@media (forced-colors: active) {
  .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox:hover > .mdc-checkbox__native-control ~ .mdc-checkbox__background,
  .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox .mdc-checkbox__native-control:focus ~ .mdc-checkbox__background,
  .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__background {
    border-color: GrayText;
  }
}
.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__native-control:checked ~ .mdc-checkbox__background,
.mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__native-control:indeterminate ~ .mdc-checkbox__background {
  background-color: var(--mat-checkbox-disabled-selected-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent));
  border-color: transparent;
}

.mdc-checkbox__checkmark {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  opacity: 0;
  transition: opacity 180ms cubic-bezier(0.4, 0, 0.6, 1);
  color: var(--mat-checkbox-selected-checkmark-color, var(--mat-sys-on-primary));
}
@media (forced-colors: active) {
  .mdc-checkbox__checkmark {
    color: CanvasText;
  }
}

.mdc-checkbox--disabled .mdc-checkbox__checkmark, .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__checkmark {
  color: var(--mat-checkbox-disabled-selected-checkmark-color, var(--mat-sys-surface));
}
@media (forced-colors: active) {
  .mdc-checkbox--disabled .mdc-checkbox__checkmark, .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__checkmark {
    color: GrayText;
  }
}

.mdc-checkbox__checkmark-path {
  transition: stroke-dashoffset 180ms cubic-bezier(0.4, 0, 0.6, 1);
  stroke: currentColor;
  stroke-width: 3.12px;
  stroke-dashoffset: 29.7833385;
  stroke-dasharray: 29.7833385;
}

.mdc-checkbox__mixedmark {
  width: 100%;
  height: 0;
  transform: scaleX(0) rotate(0deg);
  border-width: 1px;
  border-style: solid;
  opacity: 0;
  transition: opacity 90ms cubic-bezier(0.4, 0, 0.6, 1), transform 90ms cubic-bezier(0.4, 0, 0.6, 1);
  border-color: var(--mat-checkbox-selected-checkmark-color, var(--mat-sys-on-primary));
}
@media (forced-colors: active) {
  .mdc-checkbox__mixedmark {
    margin: 0 1px;
  }
}

.mdc-checkbox--disabled .mdc-checkbox__mixedmark, .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__mixedmark {
  border-color: var(--mat-checkbox-disabled-selected-checkmark-color, var(--mat-sys-surface));
}
@media (forced-colors: active) {
  .mdc-checkbox--disabled .mdc-checkbox__mixedmark, .mdc-checkbox--disabled.mat-mdc-checkbox-disabled-interactive .mdc-checkbox__mixedmark {
    border-color: GrayText;
  }
}

.mdc-checkbox--anim-unchecked-checked .mdc-checkbox__background,
.mdc-checkbox--anim-unchecked-indeterminate .mdc-checkbox__background,
.mdc-checkbox--anim-checked-unchecked .mdc-checkbox__background,
.mdc-checkbox--anim-indeterminate-unchecked .mdc-checkbox__background {
  animation-duration: 180ms;
  animation-timing-function: linear;
}

.mdc-checkbox--anim-unchecked-checked .mdc-checkbox__checkmark-path {
  animation: mdc-checkbox-unchecked-checked-checkmark-path 180ms linear;
  transition: none;
}

.mdc-checkbox--anim-unchecked-indeterminate .mdc-checkbox__mixedmark {
  animation: mdc-checkbox-unchecked-indeterminate-mixedmark 90ms linear;
  transition: none;
}

.mdc-checkbox--anim-checked-unchecked .mdc-checkbox__checkmark-path {
  animation: mdc-checkbox-checked-unchecked-checkmark-path 90ms linear;
  transition: none;
}

.mdc-checkbox--anim-checked-indeterminate .mdc-checkbox__checkmark {
  animation: mdc-checkbox-checked-indeterminate-checkmark 90ms linear;
  transition: none;
}
.mdc-checkbox--anim-checked-indeterminate .mdc-checkbox__mixedmark {
  animation: mdc-checkbox-checked-indeterminate-mixedmark 90ms linear;
  transition: none;
}

.mdc-checkbox--anim-indeterminate-checked .mdc-checkbox__checkmark {
  animation: mdc-checkbox-indeterminate-checked-checkmark 500ms linear;
  transition: none;
}
.mdc-checkbox--anim-indeterminate-checked .mdc-checkbox__mixedmark {
  animation: mdc-checkbox-indeterminate-checked-mixedmark 500ms linear;
  transition: none;
}

.mdc-checkbox--anim-indeterminate-unchecked .mdc-checkbox__mixedmark {
  animation: mdc-checkbox-indeterminate-unchecked-mixedmark 300ms linear;
  transition: none;
}

.mdc-checkbox__native-control:checked ~ .mdc-checkbox__background,
.mdc-checkbox__native-control:indeterminate ~ .mdc-checkbox__background {
  transition: border-color 90ms cubic-bezier(0, 0, 0.2, 1), background-color 90ms cubic-bezier(0, 0, 0.2, 1);
}
.mdc-checkbox__native-control:checked ~ .mdc-checkbox__background > .mdc-checkbox__checkmark > .mdc-checkbox__checkmark-path,
.mdc-checkbox__native-control:indeterminate ~ .mdc-checkbox__background > .mdc-checkbox__checkmark > .mdc-checkbox__checkmark-path {
  stroke-dashoffset: 0;
}

.mdc-checkbox__native-control:checked ~ .mdc-checkbox__background > .mdc-checkbox__checkmark {
  transition: opacity 180ms cubic-bezier(0, 0, 0.2, 1), transform 180ms cubic-bezier(0, 0, 0.2, 1);
  opacity: 1;
}
.mdc-checkbox__native-control:checked ~ .mdc-checkbox__background > .mdc-checkbox__mixedmark {
  transform: scaleX(1) rotate(-45deg);
}

.mdc-checkbox__native-control:indeterminate ~ .mdc-checkbox__background > .mdc-checkbox__checkmark {
  transform: rotate(45deg);
  opacity: 0;
  transition: opacity 90ms cubic-bezier(0.4, 0, 0.6, 1), transform 90ms cubic-bezier(0.4, 0, 0.6, 1);
}
.mdc-checkbox__native-control:indeterminate ~ .mdc-checkbox__background > .mdc-checkbox__mixedmark {
  transform: scaleX(1) rotate(0deg);
  opacity: 1;
}

@keyframes mdc-checkbox-unchecked-checked-checkmark-path {
  0%, 50% {
    stroke-dashoffset: 29.7833385;
  }
  50% {
    animation-timing-function: cubic-bezier(0, 0, 0.2, 1);
  }
  100% {
    stroke-dashoffset: 0;
  }
}
@keyframes mdc-checkbox-unchecked-indeterminate-mixedmark {
  0%, 68.2% {
    transform: scaleX(0);
  }
  68.2% {
    animation-timing-function: cubic-bezier(0, 0, 0, 1);
  }
  100% {
    transform: scaleX(1);
  }
}
@keyframes mdc-checkbox-checked-unchecked-checkmark-path {
  from {
    animation-timing-function: cubic-bezier(0.4, 0, 1, 1);
    opacity: 1;
    stroke-dashoffset: 0;
  }
  to {
    opacity: 0;
    stroke-dashoffset: -29.7833385;
  }
}
@keyframes mdc-checkbox-checked-indeterminate-checkmark {
  from {
    animation-timing-function: cubic-bezier(0, 0, 0.2, 1);
    transform: rotate(0deg);
    opacity: 1;
  }
  to {
    transform: rotate(45deg);
    opacity: 0;
  }
}
@keyframes mdc-checkbox-indeterminate-checked-checkmark {
  from {
    animation-timing-function: cubic-bezier(0.14, 0, 0, 1);
    transform: rotate(45deg);
    opacity: 0;
  }
  to {
    transform: rotate(360deg);
    opacity: 1;
  }
}
@keyframes mdc-checkbox-checked-indeterminate-mixedmark {
  from {
    animation-timing-function: cubic-bezier(0, 0, 0.2, 1);
    transform: rotate(-45deg);
    opacity: 0;
  }
  to {
    transform: rotate(0deg);
    opacity: 1;
  }
}
@keyframes mdc-checkbox-indeterminate-checked-mixedmark {
  from {
    animation-timing-function: cubic-bezier(0.14, 0, 0, 1);
    transform: rotate(0deg);
    opacity: 1;
  }
  to {
    transform: rotate(315deg);
    opacity: 0;
  }
}
@keyframes mdc-checkbox-indeterminate-unchecked-mixedmark {
  0% {
    animation-timing-function: linear;
    transform: scaleX(1);
    opacity: 1;
  }
  32.8%, 100% {
    transform: scaleX(0);
    opacity: 0;
  }
}
.mat-mdc-checkbox {
  display: inline-block;
  position: relative;
  -webkit-tap-highlight-color: transparent;
}
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mat-mdc-checkbox-touch-target,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mdc-checkbox__native-control,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mdc-checkbox__ripple,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mat-mdc-checkbox-ripple::before,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mdc-checkbox__background,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mdc-checkbox__background > .mdc-checkbox__checkmark,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mdc-checkbox__background > .mdc-checkbox__checkmark > .mdc-checkbox__checkmark-path,
.mat-mdc-checkbox._mat-animation-noopable > .mat-internal-form-field > .mdc-checkbox > .mdc-checkbox__background > .mdc-checkbox__mixedmark {
  transition: none !important;
  animation: none !important;
}
.mat-mdc-checkbox label {
  cursor: pointer;
}
.mat-mdc-checkbox .mat-internal-form-field {
  color: var(--mat-checkbox-label-text-color, var(--mat-sys-on-surface));
  font-family: var(--mat-checkbox-label-text-font, var(--mat-sys-body-medium-font));
  line-height: var(--mat-checkbox-label-text-line-height, var(--mat-sys-body-medium-line-height));
  font-size: var(--mat-checkbox-label-text-size, var(--mat-sys-body-medium-size));
  letter-spacing: var(--mat-checkbox-label-text-tracking, var(--mat-sys-body-medium-tracking));
  font-weight: var(--mat-checkbox-label-text-weight, var(--mat-sys-body-medium-weight));
}
.mat-mdc-checkbox.mat-mdc-checkbox-disabled.mat-mdc-checkbox-disabled-interactive {
  pointer-events: auto;
}
.mat-mdc-checkbox.mat-mdc-checkbox-disabled.mat-mdc-checkbox-disabled-interactive input {
  cursor: default;
}
.mat-mdc-checkbox.mat-mdc-checkbox-disabled label {
  cursor: default;
  color: var(--mat-checkbox-disabled-label-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent));
}
@media (forced-colors: active) {
  .mat-mdc-checkbox.mat-mdc-checkbox-disabled label {
    color: GrayText;
  }
}
.mat-mdc-checkbox label:empty {
  display: none;
}
.mat-mdc-checkbox .mdc-checkbox__ripple {
  opacity: 0;
}

.mat-mdc-checkbox .mat-mdc-checkbox-ripple,
.mdc-checkbox__ripple {
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.mat-mdc-checkbox .mat-mdc-checkbox-ripple:not(:empty),
.mdc-checkbox__ripple:not(:empty) {
  transform: translateZ(0);
}

.mat-mdc-checkbox-ripple .mat-ripple-element {
  opacity: 0.1;
}

.mat-mdc-checkbox-touch-target {
  position: absolute;
  top: 50%;
  left: 50%;
  height: var(--mat-checkbox-touch-target-size, 48px);
  width: var(--mat-checkbox-touch-target-size, 48px);
  transform: translate(-50%, -50%);
  display: var(--mat-checkbox-touch-target-display, block);
}

.mat-mdc-checkbox .mat-mdc-checkbox-ripple::before {
  border-radius: 50%;
}

.mdc-checkbox__native-control:focus-visible ~ .mat-focus-indicator::before {
  content: "";
}
`],encapsulation:2})}return a})(),Oe=(()=>{class a{static \u0275fac=function(n){return new(n||a)};static \u0275mod=tE({type:a});static \u0275inj=Hl({imports:[ct,me]})}return a})();function ga(a,o){if(a&1&&(fi(0,"mat-option",5),vD(1),Sc()),a&2){let e=o.$implicit;Mp("value",e),Tv(),Kp(e);}}var ze=class a{fb=T(cb);dialogRef=T(v);data=T(se);stepTypes=["ENV_SETUP","LOG_CLEANUP","JAVA_EXEC","SFTP","ARCHIVE"];form;constructor(){this.form=this.fb.group({stepName:[this.data.stepName,Er.required],stepType:[this.data.stepType,Er.required],stepConfig:[this.data.stepConfig||"{}"],continueOnFailure:[this.data.continueOnFailure],enabled:[this.data.enabled]});}onSubmit(){this.form.invalid||this.dialogRef.close(q({stepId:this.data.stepId},this.form.value));}onCancel(){this.dialogRef.close();}static \u0275fac=function(e){return new(e||a)};static \u0275cmp=XI({type:a,selectors:[["app-step-form-dialog"]],decls:28,vars:2,consts:[["mat-dialog-title",""],[3,"formGroup"],["appearance","outline"],["matInput","","formControlName","stepName","required",""],["formControlName","stepType"],[3,"value"],["matInput","","formControlName","stepConfig","rows","4"],[1,"checkbox-row"],["formControlName","continueOnFailure"],["formControlName","enabled"],["align","end"],["mat-button","",3,"click"],["mat-flat-button","","color","primary",3,"click"]],template:function(e,n){e&1&&(fi(0,"h2",0),vD(1),Sc(),fi(2,"mat-dialog-content")(3,"form",1)(4,"mat-form-field",2)(5,"mat-label"),vD(6,"Step Name"),Sc(),Np(7,"input",3),uI(),Sc(),fi(8,"mat-form-field",2)(9,"mat-label"),vD(10,"Step Type"),Sc(),fi(11,"mat-select",4),NE(12,ga,2,2,"mat-option",5,ME),Sc(),uI(),Sc(),fi(14,"mat-form-field",2)(15,"mat-label"),vD(16,"Step Config (JSON)"),Sc(),Np(17,"textarea",6),uI(),Sc(),fi(18,"div",7)(19,"mat-checkbox",8),vD(20,"Continue on failure"),Sc(),uI(),fi(21,"mat-checkbox",9),vD(22,"Enabled"),Sc(),uI(),Sc()()(),fi(23,"mat-dialog-actions",10)(24,"button",11),Op("click",function(){return n.onCancel()}),vD(25,"Cancel"),Sc(),fi(26,"button",12),Op("click",function(){return n.onSubmit()}),vD(27,"Save"),Sc()()),e&2&&(Tv(),Kp(n.data.stepId?"Edit Step":"Add Step"),Tv(2),Mp("formGroup",n.form),Tv(4),fI(),Tv(4),fI(),Tv(),SE(n.stepTypes),Tv(5),fI(),Tv(2),fI(),Tv(2),fI());},dependencies:[Go,db,ab,ha,rb,ob,Aa$1,Kd,Xd,I,Ci,Qe,Ke$1,Ge$1,D_,y_,bi,yi$1,j,Oe,ct,Ue,We,qe,Qe$1],styles:["mat-dialog-content[_ngcontent-%COMP%]{display:flex;flex-direction:column;gap:16px;min-width:350px}.checkbox-row[_ngcontent-%COMP%]{display:flex;gap:24px}"]})};function ka(a,o){a&1&&(fi(0,"mat-error"),vD(1,"Job name is required"),Sc());}function va(a,o){a&1&&(fi(0,"mat-error"),vD(1,"Working directory is required"),Sc());}function xa(a,o){if(a&1){let e=PE();fi(0,"button",13),Op("click",function(){du(e);let t=$E(2);return fu(t.saveGeneral())}),vD(1,"Save"),Sc();}}function ya(a,o){if(a&1){let e=PE();fi(0,"button",13),Op("click",function(){du(e);let t=$E(2);return fu(t.createNewJob())}),vD(1,"Create Job"),Sc();}}function Ca(a,o){a&1&&(fi(0,"th",27),vD(1,"#"),Sc());}function Ta(a,o){if(a&1&&(fi(0,"td",28),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Kp(e.stepOrder);}}function Ia(a,o){a&1&&(fi(0,"th",27),vD(1,"Name"),Sc());}function wa(a,o){if(a&1&&(fi(0,"td",28),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Kp(e.stepName);}}function Da(a,o){a&1&&(fi(0,"th",27),vD(1,"Type"),Sc());}function Ea(a,o){if(a&1&&(fi(0,"td",28)(1,"mat-chip"),vD(2),Sc()()),a&2){let e=o.$implicit;Tv(2),Kp(e.stepType);}}function Sa(a,o){a&1&&(fi(0,"th",27),vD(1,"Continue on Fail"),Sc());}function Ma(a,o){if(a&1&&(fi(0,"td",28),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Oc(" ",e.continueOnFailure?"Yes":"No"," ");}}function Ra(a,o){a&1&&(fi(0,"th",27),vD(1,"Enabled"),Sc());}function Pa(a,o){if(a&1&&(fi(0,"td",28),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Oc(" ",e.enabled?"Yes":"No"," ");}}function Ba(a,o){a&1&&(fi(0,"th",27),vD(1,"Actions"),Sc());}function La(a,o){if(a&1){let e=PE();fi(0,"td",28)(1,"button",29),Op("click",function(){let t=du(e).$implicit,i=$E(3);return fu(i.openStepForm(t))}),fi(2,"mat-icon"),vD(3,"edit"),Sc()(),fi(4,"button",30),Op("click",function(){let t=du(e).$implicit,i=$E(3);return fu(i.deleteStep(t))}),fi(5,"mat-icon"),vD(6,"delete"),Sc()()();}}function Aa(a,o){a&1&&Np(0,"tr",31);}function Fa(a,o){a&1&&Np(0,"tr",32);}function Na(a,o){if(a&1){let e=PE();fi(0,"mat-tab",10)(1,"div",14)(2,"button",15),Op("click",function(){du(e);let t=$E(2);return fu(t.openStepForm())}),fi(3,"mat-icon"),vD(4,"add"),Sc(),vD(5," Add Step "),Sc(),fi(6,"table",16),Rc(7,17),Ep(8,Ca,2,0,"th",18)(9,Ta,2,1,"td",19),kc(),Rc(10,20),Ep(11,Ia,2,0,"th",18)(12,wa,2,1,"td",19),kc(),Rc(13,21),Ep(14,Da,2,0,"th",18)(15,Ea,3,1,"td",19),kc(),Rc(16,22),Ep(17,Sa,2,0,"th",18)(18,Ma,2,1,"td",19),kc(),Rc(19,23),Ep(20,Ra,2,0,"th",18)(21,Pa,2,1,"td",19),kc(),Rc(22,24),Ep(23,Ba,2,0,"th",18)(24,La,7,0,"td",19),kc(),Ep(25,Aa,1,0,"tr",25)(26,Fa,1,0,"tr",26),Sc()()();}if(a&2){let e=$E(2);Mp("label",wD("Steps (",e.job.steps.length,")")),Tv(6),Mp("dataSource",e.job.steps),Tv(19),Mp("matHeaderRowDef",e.displayedStepCols),Tv(),Mp("matRowDefColumns",e.displayedStepCols);}}function ja(a,o){a&1&&(fi(0,"th",27),vD(1,"Variable"),Sc());}function Va(a,o){if(a&1&&(fi(0,"td",28),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Kp(e.key);}}function Oa(a,o){a&1&&(fi(0,"th",27),vD(1,"Value"),Sc());}function za(a,o){if(a&1&&(fi(0,"td",28),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Kp(e.value);}}function Ja(a,o){a&1&&(fi(0,"th",27),vD(1,"Actions"),Sc());}function Ha(a,o){if(a&1){let e=PE();fi(0,"td",28)(1,"button",30),Op("click",function(){let t=du(e).$implicit,i=$E(4);return fu(i.deleteEnvVar(t.envVarId))}),fi(2,"mat-icon"),vD(3,"delete"),Sc()()();}}function Ga(a,o){a&1&&Np(0,"tr",31);}function Qa(a,o){a&1&&Np(0,"tr",32);}function Wa(a,o){if(a&1&&(fi(0,"table",16),Rc(1,37),Ep(2,ja,2,0,"th",18)(3,Va,2,1,"td",19),kc(),Rc(4,38),Ep(5,Oa,2,0,"th",18)(6,za,2,1,"td",19),kc(),Rc(7,24),Ep(8,Ja,2,0,"th",18)(9,Ha,4,0,"td",19),kc(),Ep(10,Ga,1,0,"tr",25)(11,Qa,1,0,"tr",26),Sc()),a&2){let e=$E(3);Mp("dataSource",e.job.envVars),Tv(10),Mp("matHeaderRowDef",e.displayedEnvCols),Tv(),Mp("matRowDefColumns",e.displayedEnvCols);}}function qa(a,o){a&1&&(fi(0,"p",36),vD(1,"No environment variables defined."),Sc());}function $a(a,o){if(a&1){let e=PE();fi(0,"mat-tab",11)(1,"div",14)(2,"div",33)(3,"mat-form-field",5)(4,"mat-label"),vD(5,"Variable Name"),Sc(),fi(6,"input",34),th("ngModelChange",function(t){du(e);let i=$E(2);return DD(i.newEnvKey,t)||(i.newEnvKey=t),fu(t)}),Sc(),uI(),Sc(),fi(7,"mat-form-field",5)(8,"mat-label"),vD(9,"Value"),Sc(),fi(10,"input",35),th("ngModelChange",function(t){du(e);let i=$E(2);return DD(i.newEnvValue,t)||(i.newEnvValue=t),fu(t)}),Sc(),uI(),Sc(),fi(11,"button",13),Op("click",function(){du(e);let t=$E(2);return fu(t.addEnvVar())}),fi(12,"mat-icon"),vD(13,"add"),Sc(),vD(14," Add "),Sc()(),CE(15,Wa,12,3,"table",16)(16,qa,2,0,"p",36),Sc()();}if(a&2){let e=$E(2);Tv(6),eh("ngModel",e.newEnvKey),fI(),Tv(4),eh("ngModel",e.newEnvValue),fI(),Tv(5),bE(e.job.envVars.length>0?15:16);}}function Ua(a,o){if(a&1&&(fi(0,"div",43),vD(1),Sc()),a&2){let e=o.$implicit;Tv(),Kp(e);}}function Za(a,o){if(a&1&&(fi(0,"div",41)(1,"strong"),vD(2,"Next fire times:"),Sc(),NE(3,Ua,2,1,"div",43,_E),Sc()),a&2){let e=$E(3);Tv(3),SE(e.scheduleNextFires);}}function Ka(a,o){if(a&1){let e=PE();fi(0,"button",13),Op("click",function(){du(e);let t=$E(3);return fu(t.saveSchedule())}),vD(1,"Create Schedule"),Sc();}}function Xa(a,o){if(a&1){let e=PE();fi(0,"button",13),Op("click",function(){du(e);let t=$E(3);return fu(t.saveSchedule())}),vD(1,"Update Schedule"),Sc(),fi(2,"button",15),Op("click",function(){du(e);let t=$E(3);return fu(t.toggleSchedule())}),vD(3),Sc(),fi(4,"button",44),Op("click",function(){du(e);let t=$E(3);return fu(t.deleteSchedule())}),vD(5,"Delete Schedule"),Sc();}if(a&2){let e=$E(3);Tv(3),Oc(" ",e.scheduleEnabled?"Disable":"Enable"," Schedule ");}}function Ya(a,o){if(a&1){let e=PE();fi(0,"mat-tab",12)(1,"div",14)(2,"mat-form-field",5)(3,"mat-label"),vD(4,"Cron Expression"),Sc(),fi(5,"input",39),th("ngModelChange",function(t){du(e);let i=$E(2);return DD(i.scheduleCron,t)||(i.scheduleCron=t),fu(t)}),Sc(),uI(),fi(6,"button",40),Op("click",function(){du(e);let t=$E(2);return fu(t.validateCron())}),fi(7,"mat-icon"),vD(8,"fact_check"),Sc()()(),CE(9,Za,5,0,"div",41),fi(10,"div",42),CE(11,Ka,2,0,"button",9)(12,Xa,6,1),Sc()()();}if(a&2){let e=$E(2);Tv(5),eh("ngModel",e.scheduleCron),fI(),Tv(4),bE(e.scheduleNextFires.length>0?9:-1),Tv(2),bE(e.job.schedule?12:11);}}function ei(a,o){if(a&1){let e=PE();fi(0,"div",1)(1,"button",2),Op("click",function(){du(e);let t=$E();return fu(t.goBack())}),fi(2,"mat-icon"),vD(3,"arrow_back"),Sc()(),fi(4,"h2"),vD(5),Sc()(),fi(6,"mat-tab-group")(7,"mat-tab",3)(8,"form",4)(9,"mat-form-field",5)(10,"mat-label"),vD(11,"Job Name"),Sc(),Np(12,"input",6),uI(),CE(13,ka,2,0,"mat-error"),Sc(),fi(14,"mat-form-field",5)(15,"mat-label"),vD(16,"Description"),Sc(),Np(17,"textarea",7),uI(),Sc(),fi(18,"mat-form-field",5)(19,"mat-label"),vD(20,"Working Directory"),Sc(),Np(21,"input",8),uI(),CE(22,va,2,0,"mat-error"),Sc(),CE(23,xa,2,0,"button",9)(24,ya,2,0,"button",9),Sc()(),CE(25,Na,27,5,"mat-tab",10),CE(26,$a,17,3,"mat-tab",11),CE(27,Ya,13,3,"mat-tab",12),Sc();}if(a&2){let e=$E();Tv(5),Kp(e.job?"Edit: "+e.job.jobName:"New Job"),Tv(3),Mp("formGroup",e.generalForm),Tv(4),fI(),Tv(),bE(e.generalForm.get("jobName")?.invalid&&e.generalForm.get("jobName")?.touched?13:-1),Tv(4),fI(),Tv(4),fI(),Tv(),bE(e.generalForm.get("workingDir")?.invalid&&e.generalForm.get("workingDir")?.touched?22:-1),Tv(),bE(e.jobId?23:24),Tv(2),bE(e.job?25:-1),Tv(),bE(e.job?26:-1),Tv(),bE(e.job?27:-1);}}function ti(a,o){a&1&&(fi(0,"div",0),vD(1,"Loading..."),Sc());}var Bn=class a$1{fb=T(cb);jobService=T(a);systemService=T(n);snackBar=T(Mt);dialog=T(Ge);route=T(G);router=T(ce);jobId=null;job=null;loading=true;scheduleNextFires=[];generalForm=this.fb.group({jobName:["",Er.required],description:[""],workingDir:["",Er.required]});displayedStepCols=["stepOrder","stepName","stepType","continueOnFailure","enabled","actions"];displayedEnvCols=["key","value","actions"];newEnvKey="";newEnvValue="";scheduleCron="";scheduleEnabled=false;ngOnInit(){let o=this.route.snapshot.paramMap.get("id");o?(this.jobId=+o,this.loadJob()):this.loading=false;}loadJob(){this.jobId!=null&&this.jobService.getJob(this.jobId).subscribe({next:o=>{o.status==="SUCCESS"&&(this.job=o.data,this.generalForm.patchValue({jobName:this.job.jobName,description:this.job.description,workingDir:this.job.workingDir}),this.job.schedule&&(this.scheduleCron=this.job.schedule.cronExpression,this.scheduleEnabled=this.job.schedule.enabled,this.systemService.validateCron(this.scheduleCron).subscribe({next:e=>{e.status==="SUCCESS"&&e.data.valid&&(this.scheduleNextFires=e.data.nextFireTimes);}}))),this.loading=false;},error:()=>{this.loading=false;}});}saveGeneral(){if(this.generalForm.invalid||this.jobId==null)return;let o=this.generalForm.value;this.jobService.updateJob(this.jobId,{jobName:o.jobName,description:o.description,workingDir:o.workingDir}).subscribe({next:e=>{e.status==="SUCCESS"&&(this.snackBar.open("Job updated","Dismiss",{duration:3e3}),this.loadJob());}});}openStepForm(o){let e={stepId:o?.stepId,stepName:o?.stepName??"",stepOrder:o?.stepOrder??this.job?.steps.length??0,stepType:o?.stepType??"JAVA_EXEC",stepConfig:o?.stepConfig??"{}",continueOnFailure:o?.continueOnFailure??false,enabled:o?.enabled??true};this.dialog.open(ze,{data:e,width:"550px"}).afterClosed().subscribe(n=>{n&&this.jobId!=null&&(n.stepId?this.jobService.updateStep(this.jobId,n.stepId,n).subscribe({next:()=>this.loadJob()}):this.jobService.addStep(this.jobId,n).subscribe({next:()=>this.loadJob()}));});}deleteStep(o){this.jobId!=null&&this.dialog.open(Ye$1,{data:{title:"Delete Step",message:`Delete step "${o.stepName}"?`,confirmButton:"Delete"}}).afterClosed().subscribe(e=>{!e||this.jobId==null||this.jobService.deleteStep(this.jobId,o.stepId).subscribe({next:()=>this.loadJob()});});}addEnvVar(){!this.newEnvKey.trim()||!this.newEnvValue.trim()||this.jobId==null||this.jobService.addEnvVar(this.jobId,{key:this.newEnvKey,value:this.newEnvValue}).subscribe({next:()=>{this.newEnvKey="",this.newEnvValue="",this.loadJob();}});}deleteEnvVar(o){this.jobId!=null&&this.jobService.deleteEnvVar(this.jobId,o).subscribe({next:()=>this.loadJob()});}validateCron(){this.scheduleCron&&this.systemService.validateCron(this.scheduleCron).subscribe({next:o=>{o.status==="SUCCESS"&&(o.data.valid?(this.scheduleNextFires=o.data.nextFireTimes,this.snackBar.open("Cron expression is valid","Dismiss",{duration:3e3})):this.snackBar.open("Invalid cron expression","Dismiss",{duration:3e3,panelClass:"error-snackbar"}));}});}saveSchedule(){this.jobId==null||!this.scheduleCron||(this.job?.schedule?this.jobService.updateSchedule(this.jobId,this.scheduleCron).subscribe({next:()=>{this.loadJob(),this.snackBar.open("Schedule updated","Dismiss",{duration:3e3});}}):this.jobService.createSchedule(this.jobId,this.scheduleCron).subscribe({next:()=>{this.loadJob(),this.snackBar.open("Schedule created","Dismiss",{duration:3e3});}}));}toggleSchedule(){this.jobId!=null&&(this.scheduleEnabled?this.jobService.disableSchedule(this.jobId).subscribe({next:()=>this.loadJob()}):this.jobService.enableSchedule(this.jobId).subscribe({next:()=>this.loadJob()}));}deleteSchedule(){this.jobId!=null&&this.dialog.open(Ye$1,{data:{title:"Delete Schedule",message:"Remove the schedule for this job?",confirmButton:"Delete"}}).afterClosed().subscribe(o=>{!o||this.jobId==null||this.jobService.deleteSchedule(this.jobId).subscribe({next:()=>this.loadJob()});});}createNewJob(){if(this.generalForm.invalid)return;let o=this.generalForm.value;this.jobService.createJob({jobName:o.jobName,description:o.description,workingDir:o.workingDir}).subscribe({next:e=>{e.status==="SUCCESS"&&this.router.navigate(["/jobs",e.data.jobId]);}});}goBack(){this.router.navigate(["/jobs"]);}static \u0275fac=function(e){return new(e||a$1)};static \u0275cmp=XI({type:a$1,selectors:[["app-job-detail"]],decls:2,vars:1,consts:[[1,"loading"],[1,"header-row"],["mat-icon-button","","matTooltip","Back",3,"click"],["label","General"],[1,"tab-form",3,"formGroup"],["appearance","outline"],["matInput","","formControlName","jobName","required",""],["matInput","","formControlName","description","rows","3"],["matInput","","formControlName","workingDir","required",""],["mat-flat-button","","color","primary"],[3,"label"],["label","Env Variables"],["label","Schedule"],["mat-flat-button","","color","primary",3,"click"],[1,"tab-form"],["mat-stroked-button","",3,"click"],["mat-table","",3,"dataSource"],["matColumnDef","stepOrder"],["mat-header-cell","",4,"matHeaderCellDef"],["mat-cell","",4,"matCellDef"],["matColumnDef","stepName"],["matColumnDef","stepType"],["matColumnDef","continueOnFailure"],["matColumnDef","enabled"],["matColumnDef","actions"],["mat-header-row","",4,"matHeaderRowDef"],["mat-row","",4,"matRowDef","matRowDefColumns"],["mat-header-cell",""],["mat-cell",""],["mat-icon-button","","matTooltip","Edit",3,"click"],["mat-icon-button","","matTooltip","Delete","color","warn",3,"click"],["mat-header-row",""],["mat-row",""],[1,"add-env-row"],["matInput","","placeholder","MY_VAR",3,"ngModelChange","ngModel"],["matInput","","placeholder","/some/path",3,"ngModelChange","ngModel"],[1,"empty-msg"],["matColumnDef","key"],["matColumnDef","value"],["matInput","","placeholder","0 0 2 * * *",3,"ngModelChange","ngModel"],["mat-icon-button","","matSuffix","","matTooltip","Validate cron",3,"click"],[1,"next-fires"],[1,"schedule-actions"],[1,"fire-time"],["mat-stroked-button","","color","warn",3,"click"]],template:function(e,n){e&1&&CE(0,ei,28,8)(1,ti,2,0,"div",0),e&2&&bE(n.loading?1:0);},dependencies:[Go,db,ab,ha,rb,ob,Aa$1,Kd,Xd,lb,Gd,Pn,it,Rn,I,Ci,Qe,yi,xi,Ke$1,Ge$1,D_,y_,Ql,j_,U_,Zt,Ut,Qt,Kt,Wt,Vt,Xt,$t,qt,Gt,Yt,Oe,Xt$1,Ue,nn,tn,Yt$1,mt],styles:[".header-row[_ngcontent-%COMP%]{display:flex;align-items:center;gap:8px;margin-bottom:16px}.header-row[_ngcontent-%COMP%]   h2[_ngcontent-%COMP%]{margin:0}.tab-form[_ngcontent-%COMP%]{padding:16px;display:flex;flex-direction:column;gap:16px}.add-env-row[_ngcontent-%COMP%]{display:flex;gap:12px;align-items:flex-end;flex-wrap:wrap}.schedule-actions[_ngcontent-%COMP%]{display:flex;gap:12px;flex-wrap:wrap}.next-fires[_ngcontent-%COMP%]{padding:8px 0}.fire-time[_ngcontent-%COMP%]{padding:2px 0;color:var(--mat-sys-on-surface-variant)}.empty-msg[_ngcontent-%COMP%]{color:var(--mat-sys-on-surface-variant);font-style:italic}.loading[_ngcontent-%COMP%]{text-align:center;padding:48px;color:var(--mat-sys-on-surface-variant)}table[_ngcontent-%COMP%]{width:100%}"]})};export{Bn as JobDetailComponent};