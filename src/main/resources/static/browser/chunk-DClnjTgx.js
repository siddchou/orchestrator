import {z}from'./chunk-C52-ANb2.js';import {a}from'./chunk-C3Y9TWE8.js';import {G as Ge,Y as Ye,U as Ue}from'./chunk-By1TvME3.js';import {T,W as ee$1,Y as Nh,Z as Sh,X as XI,f as Xi,v as vg,k as bg,y as pu,z as Ci,A as Si,B as Xt$2,D as Mt,l as fi,m as vD,S as Sc,o as Op,R as Rc,G as Ep,H as kc,p as Tv,M as Mp,_ as Jp,a0 as tE,a1 as Hl,a2 as Un,K as Kp,N as Np,a3 as qp,a4 as Oc,P as PE,a5 as AF,a6 as jr,a7 as Sn,a8 as OF,a9 as Ve,aa as S,ab as kF,C as CE,ac as bu,ad as _u,s as bE,ae as _p,af as pr,$ as $E,F as NE,ag as ME,ah as bp,I as SE,U as du,V as fu,ai as YE}from'./main-54UMR53X.js';import {b as bi,y as yi,j}from'./chunk-BMNLhcok.js';import {K as Ke,G as Ge$1,F}from'./chunk-DBLxH_9E.js';import {Z as Zt$1,U as Ut$1,Q as Qt$1,K as Kt$1,W as Wt$1,V as Vt$1,X as Xt$1,$ as $t$1,q as qt$1,G as Gt$1,Y as Yt$1}from'./chunk-xRR4Tf3P.js';import {Y as Yt$2,m as mt}from'./chunk-CnYQVbSA.js';import {J as Ji}from'./chunk-BhmGZRC7.js';import {j as jt$1,w as we,Q as Qt$2}from'./chunk-VhmnpSac.js';function Jt(e,o){if(e&1&&(fi(0,"mat-option",17),vD(1),Sc()),e&2){let t=o.$implicit;Mp("value",t),Tv(),Oc(" ",t," ");}}function Rt(e,o){if(e&1){let t=PE();fi(0,"mat-form-field",14)(1,"mat-select",16,0),Op("selectionChange",function(i){du(t);let d=$E(2);return fu(d._changePageSize(i.value))}),NE(3,Jt,2,2,"mat-option",17,ME),Sc(),fi(5,"div",18),Op("click",function(){du(t);let i=YE(2);return fu(i.open())}),Sc()();}if(e&2){let t=$E(2);Mp("appearance",t._formFieldAppearance)("color",t.color),Tv(),Mp("value",t.pageSize)("disabled",t.disabled),bp("aria-labelledby",t._pageSizeLabelId),Mp("panelClass",t.selectConfig.panelClass||"")("disableOptionCentering",t.selectConfig.disableOptionCentering),Tv(2),SE(t._displayedPageSizeOptions);}}function Ft(e,o){if(e&1&&(fi(0,"div",15),vD(1),Sc()),e&2){let t=$E(2);Tv(),Kp(t.pageSize);}}function Nt(e,o){if(e&1&&(fi(0,"div",3)(1,"div",13),vD(2),Sc(),CE(3,Rt,6,7,"mat-form-field",14),CE(4,Ft,2,1,"div",15),Sc()),e&2){let t=$E();Tv(),_p("id",t._pageSizeLabelId),Tv(),Oc(" ",t._intl.itemsPerPageLabel," "),Tv(),bE(t._displayedPageSizeOptions.length>1?3:-1),Tv(),bE(t._displayedPageSizeOptions.length<=1?4:-1);}}function Ht(e,o){if(e&1){let t=PE();fi(0,"button",19),Op("click",function(){du(t);let i=$E();return fu(i._buttonClicked(0,i._previousButtonsDisabled()))}),bu(),fi(1,"svg",8),Np(2,"path",20),Sc()();}if(e&2){let t=$E();Mp("matTooltip",t._intl.firstPageLabel)("matTooltipDisabled",t._previousButtonsDisabled())("disabled",t._previousButtonsDisabled())("tabindex",t._previousButtonsDisabled()?-1:null),_p("aria-label",t._intl.firstPageLabel);}}function Vt(e,o){if(e&1){let t=PE();fi(0,"button",21),Op("click",function(){du(t);let i=$E();return fu(i._buttonClicked(i.getNumberOfPages()-1,i._nextButtonsDisabled()))}),bu(),fi(1,"svg",8),Np(2,"path",22),Sc()();}if(e&2){let t=$E();Mp("matTooltip",t._intl.lastPageLabel)("matTooltipDisabled",t._nextButtonsDisabled())("disabled",t._nextButtonsDisabled())("tabindex",t._nextButtonsDisabled()?-1:null),_p("aria-label",t._intl.lastPageLabel);}}var $t=(()=>{class e{changes=new ee$1;itemsPerPageLabel="Items per page:";nextPageLabel="Next page";previousPageLabel="Previous page";firstPageLabel="First page";lastPageLabel="Last page";getRangeLabel=(t,n,i)=>{if(i==0||n==0)return `0 of ${i}`;i=Math.max(i,0);let d=t*n,z=d<i?Math.min(d+n,i):d+n;return `${d+1} \u2013 ${z} of ${i}`};static \u0275fac=function(n){return new(n||e)};static \u0275prov=pr({token:e,factory:e.\u0275fac})}return e})(),Gt=50;var Ut=new S("MAT_PAGINATOR_DEFAULT_OPTIONS"),Yt=(()=>{class e{_intl=T($t);_changeDetectorRef=T(AF);_formFieldAppearance;_pageSizeLabelId=T(jr).getId("mat-paginator-page-size-label-");_intlChanges;_isInitialized=false;_initializedStream=new Sn(1);color;get pageIndex(){return this._pageIndex}set pageIndex(t){this._pageIndex=Math.max(t||0,0),this._changeDetectorRef.markForCheck();}_pageIndex=0;get length(){return this._length}set length(t){this._length=t||0,this._changeDetectorRef.markForCheck();}_length=0;get pageSize(){return this._pageSize}set pageSize(t){this._pageSize=Math.max(t||0,0),this._updateDisplayedPageSizeOptions();}_pageSize;get pageSizeOptions(){return this._pageSizeOptions}set pageSizeOptions(t){this._pageSizeOptions=(t||[]).map(n=>OF(n,0)),this._updateDisplayedPageSizeOptions();}_pageSizeOptions=[];hidePageSize=false;showFirstLastButtons=false;selectConfig={};disabled=false;page=new Ve;_displayedPageSizeOptions;initialized=this._initializedStream;constructor(){let t=this._intl,n=T(Ut,{optional:true});if(this._intlChanges=t.changes.subscribe(()=>this._changeDetectorRef.markForCheck()),n){let{pageSize:i,pageSizeOptions:d,hidePageSize:z,showFirstLastButtons:F}=n;i!=null&&(this._pageSize=i),d!=null&&(this._pageSizeOptions=d),z!=null&&(this.hidePageSize=z),F!=null&&(this.showFirstLastButtons=F);}this._formFieldAppearance=n?.formFieldAppearance||"outline";}ngOnInit(){this._isInitialized=true,this._updateDisplayedPageSizeOptions(),this._initializedStream.next();}ngOnDestroy(){this._initializedStream.complete(),this._intlChanges.unsubscribe();}nextPage(){this.hasNextPage()&&this._navigate(this.pageIndex+1);}previousPage(){this.hasPreviousPage()&&this._navigate(this.pageIndex-1);}firstPage(){this.hasPreviousPage()&&this._navigate(0);}lastPage(){this.hasNextPage()&&this._navigate(this.getNumberOfPages()-1);}hasPreviousPage(){return this.pageIndex>=1&&this.pageSize!=0}hasNextPage(){let t=this.getNumberOfPages()-1;return this.pageIndex<t&&this.pageSize!=0}getNumberOfPages(){return this.pageSize?Math.ceil(this.length/this.pageSize):0}_changePageSize(t){let n=this.pageIndex*this.pageSize,i=this.pageIndex;this.pageIndex=Math.floor(n/t)||0,this.pageSize=t,this._emitPageEvent(i);}_nextButtonsDisabled(){return this.disabled||!this.hasNextPage()}_previousButtonsDisabled(){return this.disabled||!this.hasPreviousPage()}_updateDisplayedPageSizeOptions(){this._isInitialized&&(this.pageSize||(this._pageSize=this.pageSizeOptions.length!=0?this.pageSizeOptions[0]:Gt),this._displayedPageSizeOptions=this.pageSizeOptions.slice(),this._displayedPageSizeOptions.indexOf(this.pageSize)===-1&&this._displayedPageSizeOptions.push(this.pageSize),this._displayedPageSizeOptions.sort((t,n)=>t-n),this._changeDetectorRef.markForCheck());}_emitPageEvent(t){this.page.emit({previousPageIndex:t,pageIndex:this.pageIndex,pageSize:this.pageSize,length:this.length});}_navigate(t){let n=this.pageIndex;t!==n&&(this.pageIndex=t,this._emitPageEvent(n));}_buttonClicked(t,n){n||this._navigate(t);}static \u0275fac=function(n){return new(n||e)};static \u0275cmp=XI({type:e,selectors:[["mat-paginator"]],hostAttrs:["role","group",1,"mat-mdc-paginator"],inputs:{color:"color",pageIndex:[2,"pageIndex","pageIndex",OF],length:[2,"length","length",OF],pageSize:[2,"pageSize","pageSize",OF],pageSizeOptions:"pageSizeOptions",hidePageSize:[2,"hidePageSize","hidePageSize",kF],showFirstLastButtons:[2,"showFirstLastButtons","showFirstLastButtons",kF],selectConfig:"selectConfig",disabled:[2,"disabled","disabled",kF]},outputs:{page:"page"},exportAs:["matPaginator"],decls:14,vars:14,consts:[["selectRef",""],[1,"mat-mdc-paginator-outer-container"],[1,"mat-mdc-paginator-container"],[1,"mat-mdc-paginator-page-size"],[1,"mat-mdc-paginator-range-actions"],["aria-atomic","true","aria-live","polite","role","status",1,"mat-mdc-paginator-range-label"],["matIconButton","","type","button","matTooltipPosition","above","disabledInteractive","",1,"mat-mdc-paginator-navigation-first",3,"matTooltip","matTooltipDisabled","disabled","tabindex"],["matIconButton","","type","button","matTooltipPosition","above","disabledInteractive","",1,"mat-mdc-paginator-navigation-previous",3,"click","matTooltip","matTooltipDisabled","disabled","tabindex"],["viewBox","0 0 24 24","focusable","false","aria-hidden","true",1,"mat-mdc-paginator-icon"],["d","M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"],["matIconButton","","type","button","matTooltipPosition","above","disabledInteractive","",1,"mat-mdc-paginator-navigation-next",3,"click","matTooltip","matTooltipDisabled","disabled","tabindex"],["d","M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"],["matIconButton","","type","button","matTooltipPosition","above","disabledInteractive","",1,"mat-mdc-paginator-navigation-last",3,"matTooltip","matTooltipDisabled","disabled","tabindex"],["aria-hidden","true",1,"mat-mdc-paginator-page-size-label"],[1,"mat-mdc-paginator-page-size-select",3,"appearance","color"],[1,"mat-mdc-paginator-page-size-value"],["hideSingleSelectionIndicator","",3,"selectionChange","value","disabled","aria-labelledby","panelClass","disableOptionCentering"],[3,"value"],[1,"mat-mdc-paginator-touch-target",3,"click"],["matIconButton","","type","button","matTooltipPosition","above","disabledInteractive","",1,"mat-mdc-paginator-navigation-first",3,"click","matTooltip","matTooltipDisabled","disabled","tabindex"],["d","M18.41 16.59L13.82 12l4.59-4.59L17 6l-6 6 6 6zM6 6h2v12H6z"],["matIconButton","","type","button","matTooltipPosition","above","disabledInteractive","",1,"mat-mdc-paginator-navigation-last",3,"click","matTooltip","matTooltipDisabled","disabled","tabindex"],["d","M5.59 7.41L10.18 12l-4.59 4.59L7 18l6-6-6-6zM16 6h2v12h-2z"]],template:function(n,i){n&1&&(fi(0,"div",1)(1,"div",2),CE(2,Nt,5,4,"div",3),fi(3,"div",4)(4,"div",5),vD(5),Sc(),CE(6,Ht,3,5,"button",6),fi(7,"button",7),Op("click",function(){return i._buttonClicked(i.pageIndex-1,i._previousButtonsDisabled())}),bu(),fi(8,"svg",8),Np(9,"path",9),Sc()(),_u(),fi(10,"button",10),Op("click",function(){return i._buttonClicked(i.pageIndex+1,i._nextButtonsDisabled())}),bu(),fi(11,"svg",8),Np(12,"path",11),Sc()(),CE(13,Vt,3,5,"button",12),Sc()()()),n&2&&(Tv(2),bE(i.hidePageSize?-1:2),Tv(3),Oc(" ",i._intl.getRangeLabel(i.pageIndex,i.pageSize,i.length)," "),Tv(),bE(i.showFirstLastButtons?6:-1),Tv(),Mp("matTooltip",i._intl.previousPageLabel)("matTooltipDisabled",i._previousButtonsDisabled())("disabled",i._previousButtonsDisabled())("tabindex",i._previousButtonsDisabled()?-1:null),_p("aria-label",i._intl.previousPageLabel),Tv(3),Mp("matTooltip",i._intl.nextPageLabel)("matTooltipDisabled",i._nextButtonsDisabled())("disabled",i._nextButtonsDisabled())("tabindex",i._nextButtonsDisabled()?-1:null),_p("aria-label",i._intl.nextPageLabel),Tv(3),bE(i.showFirstLastButtons?13:-1));},dependencies:[jt$1,yi,j,pu,mt],styles:[`.mat-mdc-paginator {
  display: block;
  -moz-osx-font-smoothing: grayscale;
  -webkit-font-smoothing: antialiased;
  color: var(--mat-paginator-container-text-color, var(--mat-sys-on-surface));
  background-color: var(--mat-paginator-container-background-color, var(--mat-sys-surface));
  font-family: var(--mat-paginator-container-text-font, var(--mat-sys-body-small-font));
  line-height: var(--mat-paginator-container-text-line-height, var(--mat-sys-body-small-line-height));
  font-size: var(--mat-paginator-container-text-size, var(--mat-sys-body-small-size));
  font-weight: var(--mat-paginator-container-text-weight, var(--mat-sys-body-small-weight));
  letter-spacing: var(--mat-paginator-container-text-tracking, var(--mat-sys-body-small-tracking));
  --mat-form-field-container-height: var(--mat-paginator-form-field-container-height, 40px);
  --mat-form-field-container-vertical-padding: var(--mat-paginator-form-field-container-vertical-padding, 8px);
}
.mat-mdc-paginator .mat-mdc-select-value {
  font-size: var(--mat-paginator-select-trigger-text-size, var(--mat-sys-body-small-size));
}
.mat-mdc-paginator .mat-mdc-form-field-subscript-wrapper {
  display: none;
}
.mat-mdc-paginator .mat-mdc-select {
  line-height: 1.5;
}

.mat-mdc-paginator-outer-container {
  display: flex;
}

.mat-mdc-paginator-container {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 8px;
  flex-wrap: wrap;
  width: 100%;
  min-height: var(--mat-paginator-container-size, 56px);
}

.mat-mdc-paginator-page-size {
  display: flex;
  align-items: baseline;
  margin-right: 8px;
}
[dir=rtl] .mat-mdc-paginator-page-size {
  margin-right: 0;
  margin-left: 8px;
}

.mat-mdc-paginator-page-size-label {
  margin: 0 4px;
}

.mat-mdc-paginator-page-size-select {
  margin: 0 4px;
  width: var(--mat-paginator-page-size-select-width, 84px);
}

.mat-mdc-paginator-range-label {
  margin: 0 32px 0 24px;
}

.mat-mdc-paginator-range-actions {
  display: flex;
  align-items: center;
}

.mat-mdc-paginator-icon {
  display: inline-block;
  width: 28px;
  fill: var(--mat-paginator-enabled-icon-color, var(--mat-sys-on-surface-variant));
}
.mat-mdc-icon-button[aria-disabled] .mat-mdc-paginator-icon {
  fill: var(--mat-paginator-disabled-icon-color, color-mix(in srgb, var(--mat-sys-on-surface) 38%, transparent));
}
[dir=rtl] .mat-mdc-paginator-icon {
  transform: rotate(180deg);
}

@media (forced-colors: active) {
  .mat-mdc-icon-button[aria-disabled] .mat-mdc-paginator-icon,
  .mat-mdc-paginator-icon {
    fill: currentColor;
  }
  .mat-mdc-paginator-range-actions .mat-mdc-icon-button {
    outline: solid 1px;
  }
  .mat-mdc-paginator-range-actions .mat-mdc-icon-button[aria-disabled] {
    color: GrayText;
  }
}
.mat-mdc-paginator-touch-target {
  display: var(--mat-paginator-touch-target-display, block);
  position: absolute;
  top: 50%;
  left: 50%;
  width: var(--mat-paginator-page-size-select-width, 84px);
  height: var(--mat-paginator-page-size-select-touch-target-height, 48px);
  background-color: transparent;
  transform: translate(-50%, -50%);
  cursor: pointer;
}
`],encapsulation:2})}return e})(),jt=(()=>{class e{static \u0275fac=function(n){return new(n||e)};static \u0275mod=tE({type:e});static \u0275inj=Hl({imports:[vg,bi,Yt$2,Yt]})}return e})();var Bt=(()=>{class e{static \u0275fac=function(n){return new(n||e)};static \u0275mod=tE({type:e});static \u0275inj=Hl({imports:[Un]})}return e})();function qt(e,o){e&1&&(fi(0,"th",19),vD(1,"Job Name"),Sc());}function Zt(e,o){if(e&1&&(fi(0,"td",20)(1,"a",21),vD(2),Sc()()),e&2){let t=o.$implicit;Tv(),Mp("routerLink","/jobs/"+t.jobId),Tv(),Kp(t.jobName);}}function Kt(e,o){e&1&&(fi(0,"th",19),vD(1,"Description"),Sc());}function Wt(e,o){if(e&1&&(fi(0,"td",20),vD(1),Sc()),e&2){let t=o.$implicit;Tv(),Kp(t.description||"\u2014");}}function Qt(e,o){e&1&&(fi(0,"th",19),vD(1,"Enabled"),Sc());}function Xt(e,o){if(e&1&&(fi(0,"td",20),Np(1,"span",22),vD(2),Sc()),e&2){let t=o.$implicit;Tv(),qp("on",t.enabled),Tv(),Oc(" ",t.enabled?"Yes":"No"," ");}}function te(e,o){e&1&&(fi(0,"th",19),vD(1,"Steps"),Sc());}function ee(e,o){if(e&1&&(fi(0,"td",20),vD(1),Sc()),e&2){let t=o.$implicit;Tv(),Kp(t.steps?.length||0);}}function ie(e,o){e&1&&(fi(0,"th",19),vD(1,"Schedule"),Sc());}function ne(e,o){if(e&1&&(fi(0,"td",20),vD(1),Sc()),e&2){let t=o.$implicit;Tv(),Oc(" ",t.schedule?.cronExpression||"\u2014"," ");}}function ae(e,o){e&1&&(fi(0,"th",19),vD(1,"Actions"),Sc());}function oe(e,o){if(e&1){let t=PE();fi(0,"td",20)(1,"button",23)(2,"mat-icon"),vD(3,"edit"),Sc()(),fi(4,"button",24),Op("click",function(){let i=du(t).$implicit,d=$E();return fu(d.triggerRun(i))}),fi(5,"mat-icon"),vD(6,"play_arrow"),Sc()(),fi(7,"button",25),Op("click",function(){let i=du(t).$implicit,d=$E();return fu(d.toggleEnabled(i))}),fi(8,"mat-icon"),vD(9),Sc()(),fi(10,"button",26),Op("click",function(){let i=du(t).$implicit,d=$E();return fu(d.deleteJob(i))}),fi(11,"mat-icon"),vD(12,"delete"),Sc()()();}if(e&2){let t=o.$implicit;Tv(),Mp("routerLink","/jobs/"+t.jobId),Tv(8),Kp(t.enabled?"toggle_off":"toggle_on");}}function re(e,o){e&1&&Np(0,"tr",27);}function se(e,o){e&1&&Np(0,"tr",28);}var At=class e{jobService=T(a);dialog=T(Ge);jobs=[];displayedColumns=["jobName","description","enabled","steps","schedule","actions"];totalElements=0;page=0;size=20;searchSubject=new ee$1;ngOnInit(){this.loadJobs(),this.searchSubject.pipe(Nh(300),Sh()).subscribe(()=>{this.page=0,this.loadJobs();});}loadJobs(){let o=this.searchInput?.value||"";this.jobService.listJobs(this.page,this.size,o).subscribe({next:t=>{t.status==="SUCCESS"&&(this.jobs=t.data.content,this.totalElements=t.data.totalElements);}});}search(){this.searchSubject.next("");}setPage(o){this.page=o,this.loadJobs();}toggleEnabled(o){this.jobService.toggleEnabled(o.jobId).subscribe({next:t=>{t.status==="SUCCESS"&&(o.enabled=t.data.enabled);}});}deleteJob(o){this.dialog.open(Ye,{data:{title:"Delete Job",message:`Delete "${o.jobName}"? This cannot be undone.`,confirmButton:"Delete"}}).afterClosed().subscribe(t=>{t&&this.jobService.deleteJob(o.jobId).subscribe({next:()=>this.loadJobs()});});}triggerRun(o){this.dialog.open(Ye,{data:{title:"Trigger Run",message:`Run "${o.jobName}" now?`,confirmButton:"Run"}}).afterClosed().subscribe(t=>{t&&this.jobService.triggerRun(o.jobId).subscribe();});}get hasPrevious(){return this.page>0}get hasNext(){return this.page*this.size+this.jobs.length<this.totalElements}get searchInput(){return document.querySelector("#searchInput")}static \u0275fac=function(t){return new(t||e)};static \u0275cmp=XI({type:e,selectors:[["app-job-list"]],decls:43,vars:7,consts:[[1,"header-row"],[1,"header-actions"],["appearance","outline"],["matInput","","id","searchInput",3,"keyup.enter"],["matSuffix",""],["mat-flat-button","","color","primary","routerLink","/jobs/new"],["mat-table","",1,"jobs-table",3,"dataSource"],["matColumnDef","jobName"],["mat-header-cell","",4,"matHeaderCellDef"],["mat-cell","",4,"matCellDef"],["matColumnDef","description"],["matColumnDef","enabled"],["matColumnDef","steps"],["matColumnDef","schedule"],["matColumnDef","actions"],["mat-header-row","",4,"matHeaderRowDef"],["mat-row","",4,"matRowDef","matRowDefColumns"],[1,"pagination"],["mat-button","",3,"click","disabled"],["mat-header-cell",""],["mat-cell",""],[1,"job-link",3,"routerLink"],[1,"enabled-dot"],["mat-icon-button","","matTooltip","Edit",3,"routerLink"],["mat-icon-button","","matTooltip","Run now",3,"click"],["mat-icon-button","","matTooltip","Toggle enabled",3,"click"],["mat-icon-button","","matTooltip","Delete","color","warn",3,"click"],["mat-header-row",""],["mat-row",""]],template:function(t,n){t&1&&(fi(0,"div",0)(1,"h2"),vD(2,"Jobs"),Sc(),fi(3,"div",1)(4,"mat-form-field",2)(5,"mat-label"),vD(6,"Search jobs..."),Sc(),fi(7,"input",3),Op("keyup.enter",function(){return n.search()}),Sc(),fi(8,"mat-icon",4),vD(9,"search"),Sc()(),fi(10,"button",5)(11,"mat-icon"),vD(12,"add"),Sc(),fi(13,"span"),vD(14,"New Job"),Sc()()()(),fi(15,"table",6),Rc(16,7),Ep(17,qt,2,0,"th",8)(18,Zt,3,2,"td",9),kc(),Rc(19,10),Ep(20,Kt,2,0,"th",8)(21,Wt,2,1,"td",9),kc(),Rc(22,11),Ep(23,Qt,2,0,"th",8)(24,Xt,3,3,"td",9),kc(),Rc(25,12),Ep(26,te,2,0,"th",8)(27,ee,2,1,"td",9),kc(),Rc(28,13),Ep(29,ie,2,0,"th",8)(30,ne,2,1,"td",9),kc(),Rc(31,14),Ep(32,ae,2,0,"th",8)(33,oe,13,2,"td",9),kc(),Ep(34,re,1,0,"tr",15)(35,se,1,0,"tr",16),Sc(),fi(36,"div",17)(37,"button",18),Op("click",function(){return n.setPage(n.page-1)}),vD(38,"Previous"),Sc(),fi(39,"span"),vD(40),Sc(),fi(41,"button",18),Op("click",function(){return n.setPage(n.page+1)}),vD(42,"Next"),Sc()()),t&2&&(Tv(15),Mp("dataSource",n.jobs),Tv(19),Mp("matHeaderRowDef",n.displayedColumns),Tv(),Mp("matRowDefColumns",n.displayedColumns),Tv(2),Mp("disabled",!n.hasPrevious),Tv(3),Jp("Page ",n.page+1," (",n.totalElements," total)"),Tv(),Mp("disabled",!n.hasNext));},dependencies:[Xi,z,Zt$1,Ut$1,Qt$1,Kt$1,Wt$1,Vt$1,Xt$1,$t$1,qt$1,Gt$1,Yt$1,jt,Bt,Ke,Ge$1,jt$1,we,Qt$2,F,vg,bg,pu,Ci,Si,Ji,Xt$2,Ue,Mt],styles:[".header-row[_ngcontent-%COMP%]{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;flex-wrap:wrap;gap:12px}.header-actions[_ngcontent-%COMP%]{display:flex;gap:12px;align-items:center}.jobs-table[_ngcontent-%COMP%]{width:100%}.job-link[_ngcontent-%COMP%]{color:var(--mat-sys-primary);text-decoration:none;font-weight:500;cursor:pointer}.job-link[_ngcontent-%COMP%]:hover{text-decoration:underline}.enabled-dot[_ngcontent-%COMP%]{display:inline-block;width:10px;height:10px;border-radius:50%;background-color:#9e9e9e;margin-right:6px}.enabled-dot.on[_ngcontent-%COMP%]{background-color:#4caf50}.pagination[_ngcontent-%COMP%]{display:flex;justify-content:center;align-items:center;gap:16px;margin-top:16px}h2[_ngcontent-%COMP%]{margin:0}"]})};export{At as JobListComponent};