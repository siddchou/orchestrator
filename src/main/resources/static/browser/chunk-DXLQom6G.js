import {Z,L as L$1}from'./chunk-MTxw4_q7.js';import {m}from'./chunk-C1kVZt5K.js';import {S as S$1,P,A}from'./chunk-BbEl_KsB.js';import {Y as Yt,m as mt}from'./chunk-CnYQVbSA.js';import'./chunk-BhmGZRC7.js';import {T,ak as G,X as XI,f as Xi,g as th,i as Xf,am as Tc,v as vg,k as bg,y as pu,z as Ci,A as Si,C as CE,s as bE,b7 as M,t as te,a0 as tE,a1 as Hl,a2 as Un,aA as hr,bE as In,a5 as AF,aa as S$2,aD as et,a9 as Ve$1,ao as le,ap as Rs,aN as ch,a6 as jr,aJ as Up,ar as Cm,ab as kF,as as qE,l as fi,o as Op,N as Np,S as Sc,aP as WE,ai as YE,M as Mp,p as Tv,a3 as qp,ae as _p,au as _D,aQ as wr,aR as Mt,aS as uo,a8 as OF,aT as Rp,aL as aD,av as jp,aw as zE,ax as QE,m as vD,P as PE,J as xD,F as NE,$ as $E,_ as Jp,K as Kp,L as RD,I as SE,ac as bu,a4 as Oc,be as th$1,u as uI,bh as _E,bg as eh,r as fI,U as du,V as fu,bf as DD}from'./main-54UMR53X.js';import'./chunk-VhmnpSac.js';var Ve=["switch"],Fe=["*"];function qe(i,n){i&1&&(fi(0,"span",11),bu(),fi(1,"svg",13),Np(2,"path",14),Sc(),fi(3,"svg",15),Np(4,"path",16),Sc()());}var je=new S$2("mat-slide-toggle-default-options",{providedIn:"root",factory:()=>({disableToggleValue:false,hideIcon:false,disabledInteractive:false})}),S=class{source;checked;constructor(n,e){this.source=n,this.checked=e;}},L=(()=>{class i{_elementRef=T(hr);_focusMonitor=T(In);_changeDetectorRef=T(AF);defaults=T(je);_onChange=e=>{};_onTouched=()=>{};_validatorOnChange=()=>{};_uniqueId;_checked=false;_createChangeEvent(e){return new S(this,e)}_labelId;get buttonId(){return `${this.id||this._uniqueId}-button`}_switchElement;focus(){this._switchElement.nativeElement.focus();}_noopAnimations=et();_focused=false;name=null;id;labelPosition="after";ariaLabel=null;ariaLabelledby=null;ariaDescribedby;required=false;color;disabled=false;disableRipple=false;tabIndex=0;get checked(){return this._checked}set checked(e){this._checked=e,this._changeDetectorRef.markForCheck();}hideIcon;disabledInteractive;change=new Ve$1;toggleChange=new Ve$1;get inputId(){return `${this.id||this._uniqueId}-input`}constructor(){T(le).load(Rs);let e=T(new ch("tabindex"),{optional:true}),a=this.defaults;this.tabIndex=e==null?0:parseInt(e)||0,this.color=a.color||"accent",this.id=this._uniqueId=T(jr).getId("mat-mdc-slide-toggle-"),this.hideIcon=a.hideIcon??false,this.disabledInteractive=a.disabledInteractive??false,this._labelId=this._uniqueId+"-label";}ngAfterContentInit(){this._focusMonitor.monitor(this._elementRef,true).subscribe(e=>{e==="keyboard"||e==="program"?(this._focused=true,this._changeDetectorRef.markForCheck()):e||Promise.resolve().then(()=>{this._focused=false,this._onTouched(),this._changeDetectorRef.markForCheck();});});}ngOnChanges(e){e.required&&this._validatorOnChange();}ngOnDestroy(){this._focusMonitor.stopMonitoring(this._elementRef);}writeValue(e){this.checked=!!e;}registerOnChange(e){this._onChange=e;}registerOnTouched(e){this._onTouched=e;}validate(e){return this.required&&e.value!==true?{required:true}:null}registerOnValidatorChange(e){this._validatorOnChange=e;}setDisabledState(e){this.disabled=e,this._changeDetectorRef.markForCheck();}toggle(){this.checked=!this.checked,this._onChange(this.checked);}_emitChangeEvent(){this._onChange(this.checked),this.change.emit(this._createChangeEvent(this.checked));}_handleClick(){this.disabled||(this.toggleChange.emit(),this.defaults.disableToggleValue||(this.checked=!this.checked,this._onChange(this.checked),this.change.emit(new S(this,this.checked))));}_getAriaLabelledBy(){return this.ariaLabelledby?this.ariaLabelledby:this.ariaLabel?null:this._labelId}static \u0275fac=function(a){return new(a||i)};static \u0275cmp=XI({type:i,selectors:[["mat-slide-toggle"]],viewQuery:function(a,t){if(a&1&&jp(Ve,5),a&2){let d;zE(d=QE())&&(t._switchElement=d.first);}},hostAttrs:[1,"mat-mdc-slide-toggle"],hostVars:13,hostBindings:function(a,t){a&2&&(Rp("id",t.id),_p("tabindex",null)("aria-label",null)("name",null)("aria-labelledby",null),aD(t.color?"mat-"+t.color:""),qp("mat-mdc-slide-toggle-focused",t._focused)("mat-mdc-slide-toggle-checked",t.checked)("_mat-animation-noopable",t._noopAnimations));},inputs:{name:"name",id:"id",labelPosition:"labelPosition",ariaLabel:[0,"aria-label","ariaLabel"],ariaLabelledby:[0,"aria-labelledby","ariaLabelledby"],ariaDescribedby:[0,"aria-describedby","ariaDescribedby"],required:[2,"required","required",kF],color:"color",disabled:[2,"disabled","disabled",kF],disableRipple:[2,"disableRipple","disableRipple",kF],tabIndex:[2,"tabIndex","tabIndex",e=>e==null?0:OF(e)],checked:[2,"checked","checked",kF],hideIcon:[2,"hideIcon","hideIcon",kF],disabledInteractive:[2,"disabledInteractive","disabledInteractive",kF]},outputs:{change:"change",toggleChange:"toggleChange"},exportAs:["matSlideToggle"],features:[_D([{provide:wr,useExisting:uo(()=>i),multi:true},{provide:Mt,useExisting:i,multi:true}]),Cm],ngContentSelectors:Fe,decls:14,vars:27,consts:[["switch",""],["mat-internal-form-field","",3,"labelPosition"],["role","switch","type","button",1,"mdc-switch",3,"click","tabIndex","disabled"],[1,"mat-mdc-slide-toggle-touch-target"],[1,"mdc-switch__track"],[1,"mdc-switch__handle-track"],[1,"mdc-switch__handle"],[1,"mdc-switch__shadow"],[1,"mdc-elevation-overlay"],[1,"mdc-switch__ripple"],["mat-ripple","",1,"mat-mdc-slide-toggle-ripple","mat-focus-indicator",3,"matRippleTrigger","matRippleDisabled","matRippleCentered"],[1,"mdc-switch__icons"],[1,"mdc-label",3,"click","for"],["viewBox","0 0 24 24","aria-hidden","true",1,"mdc-switch__icon","mdc-switch__icon--on"],["d","M19.69,5.23L8.96,15.96l-4.23-4.23L2.96,13.5l6,6L21.46,7L19.69,5.23z"],["viewBox","0 0 24 24","aria-hidden","true",1,"mdc-switch__icon","mdc-switch__icon--off"],["d","M20 13H4v-2h16v2z"]],template:function(a,t){if(a&1&&(qE(),fi(0,"div",1)(1,"button",2,0),Op("click",function(){return t._handleClick()}),Np(3,"div",3)(4,"span",4),fi(5,"span",5)(6,"span",6)(7,"span",7),Np(8,"span",8),Sc(),fi(9,"span",9),Np(10,"span",10),Sc(),CE(11,qe,5,0,"span",11),Sc()()(),fi(12,"label",12),Op("click",function(Ae){return Ae.stopPropagation()}),WE(13),Sc()()),a&2){let d=YE(2);Mp("labelPosition",t.labelPosition),Tv(),qp("mdc-switch--selected",t.checked)("mdc-switch--unselected",!t.checked)("mdc-switch--checked",t.checked)("mdc-switch--disabled",t.disabled)("mat-mdc-slide-toggle-disabled-interactive",t.disabledInteractive),Mp("tabIndex",t.disabled&&!t.disabledInteractive?-1:t.tabIndex)("disabled",t.disabled&&!t.disabledInteractive),_p("id",t.buttonId)("name",t.name)("aria-label",t.ariaLabel)("aria-labelledby",t._getAriaLabelledBy())("aria-describedby",t.ariaDescribedby)("aria-required",t.required||null)("aria-checked",t.checked)("aria-disabled",t.disabled&&t.disabledInteractive?"true":null),Tv(9),Mp("matRippleTrigger",d)("matRippleDisabled",t.disableRipple||t.disabled)("matRippleCentered",true),Tv(),bE(t.hideIcon?-1:11),Tv(),Mp("for",t.buttonId),_p("id",t._labelId);}},dependencies:[Up,m],styles:[`.mdc-switch {
  align-items: center;
  background: none;
  border: none;
  cursor: pointer;
  display: inline-flex;
  flex-shrink: 0;
  margin: 0;
  outline: none;
  overflow: visible;
  padding: 0;
  position: relative;
  width: var(--mat-slide-toggle-track-width, 52px);
}
.mdc-switch.mdc-switch--disabled {
  cursor: default;
  pointer-events: none;
}
.mdc-switch.mat-mdc-slide-toggle-disabled-interactive {
  pointer-events: auto;
}

.mdc-switch__track {
  overflow: hidden;
  position: relative;
  width: 100%;
  height: var(--mat-slide-toggle-track-height, 32px);
  border-radius: var(--mat-slide-toggle-track-shape, var(--mat-sys-corner-full));
}
.mdc-switch--disabled.mdc-switch .mdc-switch__track {
  opacity: var(--mat-slide-toggle-disabled-track-opacity, 0.12);
}
.mdc-switch__track::before, .mdc-switch__track::after {
  border: 1px solid transparent;
  border-radius: inherit;
  box-sizing: border-box;
  content: "";
  height: 100%;
  left: 0;
  position: absolute;
  width: 100%;
  border-width: var(--mat-slide-toggle-track-outline-width, 2px);
  border-color: var(--mat-slide-toggle-track-outline-color, var(--mat-sys-outline));
}
.mdc-switch--selected .mdc-switch__track::before, .mdc-switch--selected .mdc-switch__track::after {
  border-width: var(--mat-slide-toggle-selected-track-outline-width, 2px);
  border-color: var(--mat-slide-toggle-selected-track-outline-color, transparent);
}
.mdc-switch--disabled .mdc-switch__track::before, .mdc-switch--disabled .mdc-switch__track::after {
  border-width: var(--mat-slide-toggle-disabled-unselected-track-outline-width, 2px);
  border-color: var(--mat-slide-toggle-disabled-unselected-track-outline-color, var(--mat-sys-on-surface));
}
@media (forced-colors: active) {
  .mdc-switch__track {
    border-color: currentColor;
  }
}
.mdc-switch__track::before {
  transition: transform 75ms 0ms cubic-bezier(0, 0, 0.2, 1);
  transform: translateX(0);
  background: var(--mat-slide-toggle-unselected-track-color, var(--mat-sys-surface-variant));
}
.mdc-switch--selected .mdc-switch__track::before {
  transition: transform 75ms 0ms cubic-bezier(0.4, 0, 0.6, 1);
  transform: translateX(100%);
}
[dir=rtl] .mdc-switch--selected .mdc-switch--selected .mdc-switch__track::before {
  transform: translateX(-100%);
}
.mdc-switch--selected .mdc-switch__track::before {
  opacity: var(--mat-slide-toggle-hidden-track-opacity, 0);
  transition: var(--mat-slide-toggle-hidden-track-transition, opacity 75ms);
}
.mdc-switch--unselected .mdc-switch__track::before {
  opacity: var(--mat-slide-toggle-visible-track-opacity, 1);
  transition: var(--mat-slide-toggle-visible-track-transition, opacity 75ms);
}
.mdc-switch:enabled:hover:not(:focus):not(:active) .mdc-switch__track::before {
  background: var(--mat-slide-toggle-unselected-hover-track-color, var(--mat-sys-surface-variant));
}
.mdc-switch:enabled:focus:not(:active) .mdc-switch__track::before {
  background: var(--mat-slide-toggle-unselected-focus-track-color, var(--mat-sys-surface-variant));
}
.mdc-switch:enabled:active .mdc-switch__track::before {
  background: var(--mat-slide-toggle-unselected-pressed-track-color, var(--mat-sys-surface-variant));
}
.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__track::before, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__track::before, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__track::before, .mdc-switch.mdc-switch--disabled .mdc-switch__track::before {
  background: var(--mat-slide-toggle-disabled-unselected-track-color, var(--mat-sys-surface-variant));
}
.mdc-switch__track::after {
  transform: translateX(-100%);
  background: var(--mat-slide-toggle-selected-track-color, var(--mat-sys-primary));
}
[dir=rtl] .mdc-switch__track::after {
  transform: translateX(100%);
}
.mdc-switch--selected .mdc-switch__track::after {
  transform: translateX(0);
}
.mdc-switch--selected .mdc-switch__track::after {
  opacity: var(--mat-slide-toggle-visible-track-opacity, 1);
  transition: var(--mat-slide-toggle-visible-track-transition, opacity 75ms);
}
.mdc-switch--unselected .mdc-switch__track::after {
  opacity: var(--mat-slide-toggle-hidden-track-opacity, 0);
  transition: var(--mat-slide-toggle-hidden-track-transition, opacity 75ms);
}
.mdc-switch:enabled:hover:not(:focus):not(:active) .mdc-switch__track::after {
  background: var(--mat-slide-toggle-selected-hover-track-color, var(--mat-sys-primary));
}
.mdc-switch:enabled:focus:not(:active) .mdc-switch__track::after {
  background: var(--mat-slide-toggle-selected-focus-track-color, var(--mat-sys-primary));
}
.mdc-switch:enabled:active .mdc-switch__track::after {
  background: var(--mat-slide-toggle-selected-pressed-track-color, var(--mat-sys-primary));
}
.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__track::after, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__track::after, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__track::after, .mdc-switch.mdc-switch--disabled .mdc-switch__track::after {
  background: var(--mat-slide-toggle-disabled-selected-track-color, var(--mat-sys-on-surface));
}

.mdc-switch__handle-track {
  height: 100%;
  pointer-events: none;
  position: absolute;
  top: 0;
  transition: transform 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1);
  left: 0;
  right: auto;
  transform: translateX(0);
  width: calc(100% - var(--mat-slide-toggle-handle-width));
}
[dir=rtl] .mdc-switch__handle-track {
  left: auto;
  right: 0;
}
.mdc-switch--selected .mdc-switch__handle-track {
  transform: translateX(100%);
}
[dir=rtl] .mdc-switch--selected .mdc-switch__handle-track {
  transform: translateX(-100%);
}

.mdc-switch__handle {
  display: flex;
  pointer-events: auto;
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  left: 0;
  right: auto;
  transition: width 75ms cubic-bezier(0.4, 0, 0.2, 1), height 75ms cubic-bezier(0.4, 0, 0.2, 1), margin 75ms cubic-bezier(0.4, 0, 0.2, 1);
  width: var(--mat-slide-toggle-handle-width);
  height: var(--mat-slide-toggle-handle-height);
  border-radius: var(--mat-slide-toggle-handle-shape, var(--mat-sys-corner-full));
}
[dir=rtl] .mdc-switch__handle {
  left: auto;
  right: 0;
}
.mat-mdc-slide-toggle .mdc-switch--unselected .mdc-switch__handle {
  width: var(--mat-slide-toggle-unselected-handle-size, 16px);
  height: var(--mat-slide-toggle-unselected-handle-size, 16px);
  margin: var(--mat-slide-toggle-unselected-handle-horizontal-margin, 0 8px);
}
.mat-mdc-slide-toggle .mdc-switch--unselected .mdc-switch__handle:has(.mdc-switch__icons) {
  margin: var(--mat-slide-toggle-unselected-with-icon-handle-horizontal-margin, 0 4px);
}
.mat-mdc-slide-toggle .mdc-switch--selected .mdc-switch__handle {
  width: var(--mat-slide-toggle-selected-handle-size, 24px);
  height: var(--mat-slide-toggle-selected-handle-size, 24px);
  margin: var(--mat-slide-toggle-selected-handle-horizontal-margin, 0 24px);
}
.mat-mdc-slide-toggle .mdc-switch--selected .mdc-switch__handle:has(.mdc-switch__icons) {
  margin: var(--mat-slide-toggle-selected-with-icon-handle-horizontal-margin, 0 24px);
}
.mat-mdc-slide-toggle .mdc-switch__handle:has(.mdc-switch__icons) {
  width: var(--mat-slide-toggle-with-icon-handle-size, 24px);
  height: var(--mat-slide-toggle-with-icon-handle-size, 24px);
}
.mat-mdc-slide-toggle .mdc-switch:active:not(.mdc-switch--disabled) .mdc-switch__handle {
  width: var(--mat-slide-toggle-pressed-handle-size, 28px);
  height: var(--mat-slide-toggle-pressed-handle-size, 28px);
}
.mat-mdc-slide-toggle .mdc-switch--selected:active:not(.mdc-switch--disabled) .mdc-switch__handle {
  margin: var(--mat-slide-toggle-selected-pressed-handle-horizontal-margin, 0 22px);
}
.mat-mdc-slide-toggle .mdc-switch--unselected:active:not(.mdc-switch--disabled) .mdc-switch__handle {
  margin: var(--mat-slide-toggle-unselected-pressed-handle-horizontal-margin, 0 2px);
}
.mdc-switch--disabled.mdc-switch--selected .mdc-switch__handle::after {
  opacity: var(--mat-slide-toggle-disabled-selected-handle-opacity, 1);
}
.mdc-switch--disabled.mdc-switch--unselected .mdc-switch__handle::after {
  opacity: var(--mat-slide-toggle-disabled-unselected-handle-opacity, 0.38);
}
.mdc-switch__handle::before, .mdc-switch__handle::after {
  border: 1px solid transparent;
  border-radius: inherit;
  box-sizing: border-box;
  content: "";
  width: 100%;
  height: 100%;
  left: 0;
  position: absolute;
  top: 0;
  transition: background-color 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1), border-color 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1);
  z-index: -1;
}
@media (forced-colors: active) {
  .mdc-switch__handle::before, .mdc-switch__handle::after {
    border-color: currentColor;
  }
}
.mdc-switch--selected:enabled .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-selected-handle-color, var(--mat-sys-on-primary));
}
.mdc-switch--selected:enabled:hover:not(:focus):not(:active) .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-selected-hover-handle-color, var(--mat-sys-primary-container));
}
.mdc-switch--selected:enabled:focus:not(:active) .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-selected-focus-handle-color, var(--mat-sys-primary-container));
}
.mdc-switch--selected:enabled:active .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-selected-pressed-handle-color, var(--mat-sys-primary-container));
}
.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:hover:not(:focus):not(:active) .mdc-switch__handle::after, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:focus:not(:active) .mdc-switch__handle::after, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:active .mdc-switch__handle::after, .mdc-switch--selected.mdc-switch--disabled .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-disabled-selected-handle-color, var(--mat-sys-surface));
}
.mdc-switch--unselected:enabled .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-unselected-handle-color, var(--mat-sys-outline));
}
.mdc-switch--unselected:enabled:hover:not(:focus):not(:active) .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-unselected-hover-handle-color, var(--mat-sys-on-surface-variant));
}
.mdc-switch--unselected:enabled:focus:not(:active) .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-unselected-focus-handle-color, var(--mat-sys-on-surface-variant));
}
.mdc-switch--unselected:enabled:active .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-unselected-pressed-handle-color, var(--mat-sys-on-surface-variant));
}
.mdc-switch--unselected.mdc-switch--disabled .mdc-switch__handle::after {
  background: var(--mat-slide-toggle-disabled-unselected-handle-color, var(--mat-sys-on-surface));
}
.mdc-switch__handle::before {
  background: var(--mat-slide-toggle-handle-surface-color);
}

.mdc-switch__shadow {
  border-radius: inherit;
  bottom: 0;
  left: 0;
  position: absolute;
  right: 0;
  top: 0;
}
.mdc-switch:enabled .mdc-switch__shadow {
  box-shadow: var(--mat-slide-toggle-handle-elevation-shadow);
}
.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__shadow, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__shadow, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__shadow, .mdc-switch.mdc-switch--disabled .mdc-switch__shadow {
  box-shadow: var(--mat-slide-toggle-disabled-handle-elevation-shadow);
}

.mdc-switch__ripple {
  left: 50%;
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: -1;
  width: var(--mat-slide-toggle-state-layer-size, 40px);
  height: var(--mat-slide-toggle-state-layer-size, 40px);
}
.mdc-switch__ripple::after {
  content: "";
  opacity: 0;
}
.mdc-switch--disabled .mdc-switch__ripple::after {
  display: none;
}
.mat-mdc-slide-toggle-disabled-interactive .mdc-switch__ripple::after {
  display: block;
}
.mdc-switch:hover .mdc-switch__ripple::after {
  transition: 75ms opacity cubic-bezier(0, 0, 0.2, 1);
}
.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:focus .mdc-switch__ripple::after, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:active .mdc-switch__ripple::after, .mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:hover:not(:focus) .mdc-switch__ripple::after, .mdc-switch--unselected:enabled:hover:not(:focus) .mdc-switch__ripple::after {
  background: var(--mat-slide-toggle-unselected-hover-state-layer-color, var(--mat-sys-on-surface));
  opacity: var(--mat-slide-toggle-unselected-hover-state-layer-opacity, var(--mat-sys-hover-state-layer-opacity));
}
.mdc-switch--unselected:enabled:focus .mdc-switch__ripple::after {
  background: var(--mat-slide-toggle-unselected-focus-state-layer-color, var(--mat-sys-on-surface));
  opacity: var(--mat-slide-toggle-unselected-focus-state-layer-opacity, var(--mat-sys-focus-state-layer-opacity));
}
.mdc-switch--unselected:enabled:active .mdc-switch__ripple::after {
  background: var(--mat-slide-toggle-unselected-pressed-state-layer-color, var(--mat-sys-on-surface));
  opacity: var(--mat-slide-toggle-unselected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));
  transition: opacity 75ms linear;
}
.mdc-switch--selected:enabled:hover:not(:focus) .mdc-switch__ripple::after {
  background: var(--mat-slide-toggle-selected-hover-state-layer-color, var(--mat-sys-primary));
  opacity: var(--mat-slide-toggle-selected-hover-state-layer-opacity, var(--mat-sys-hover-state-layer-opacity));
}
.mdc-switch--selected:enabled:focus .mdc-switch__ripple::after {
  background: var(--mat-slide-toggle-selected-focus-state-layer-color, var(--mat-sys-primary));
  opacity: var(--mat-slide-toggle-selected-focus-state-layer-opacity, var(--mat-sys-focus-state-layer-opacity));
}
.mdc-switch--selected:enabled:active .mdc-switch__ripple::after {
  background: var(--mat-slide-toggle-selected-pressed-state-layer-color, var(--mat-sys-primary));
  opacity: var(--mat-slide-toggle-selected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));
  transition: opacity 75ms linear;
}

.mdc-switch__icons {
  position: relative;
  height: 100%;
  width: 100%;
  z-index: 1;
  transform: translateZ(0);
}
.mdc-switch--disabled.mdc-switch--unselected .mdc-switch__icons {
  opacity: var(--mat-slide-toggle-disabled-unselected-icon-opacity, 0.38);
}
.mdc-switch--disabled.mdc-switch--selected .mdc-switch__icons {
  opacity: var(--mat-slide-toggle-disabled-selected-icon-opacity, 0.38);
}

.mdc-switch__icon {
  bottom: 0;
  left: 0;
  margin: auto;
  position: absolute;
  right: 0;
  top: 0;
  opacity: 0;
  transition: opacity 30ms 0ms cubic-bezier(0.4, 0, 1, 1);
}
.mdc-switch--unselected .mdc-switch__icon {
  width: var(--mat-slide-toggle-unselected-icon-size, 16px);
  height: var(--mat-slide-toggle-unselected-icon-size, 16px);
  fill: var(--mat-slide-toggle-unselected-icon-color, var(--mat-sys-surface-variant));
}
.mdc-switch--unselected.mdc-switch--disabled .mdc-switch__icon {
  fill: var(--mat-slide-toggle-disabled-unselected-icon-color, var(--mat-sys-surface-variant));
}
.mdc-switch--selected .mdc-switch__icon {
  width: var(--mat-slide-toggle-selected-icon-size, 16px);
  height: var(--mat-slide-toggle-selected-icon-size, 16px);
  fill: var(--mat-slide-toggle-selected-icon-color, var(--mat-sys-on-primary-container));
}
.mdc-switch--selected.mdc-switch--disabled .mdc-switch__icon {
  fill: var(--mat-slide-toggle-disabled-selected-icon-color, var(--mat-sys-on-surface));
}

.mdc-switch--selected .mdc-switch__icon--on,
.mdc-switch--unselected .mdc-switch__icon--off {
  opacity: 1;
  transition: opacity 45ms 30ms cubic-bezier(0, 0, 0.2, 1);
}

.mat-mdc-slide-toggle {
  -webkit-user-select: none;
  user-select: none;
  display: inline-block;
  -webkit-tap-highlight-color: transparent;
  outline: 0;
}
.mat-mdc-slide-toggle .mat-mdc-slide-toggle-ripple,
.mat-mdc-slide-toggle .mdc-switch__ripple::after {
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.mat-mdc-slide-toggle .mat-mdc-slide-toggle-ripple:not(:empty),
.mat-mdc-slide-toggle .mdc-switch__ripple::after:not(:empty) {
  transform: translateZ(0);
}
.mat-mdc-slide-toggle.mat-mdc-slide-toggle-focused .mat-focus-indicator::before {
  content: "";
}
.mat-mdc-slide-toggle .mat-internal-form-field {
  color: var(--mat-slide-toggle-label-text-color, var(--mat-sys-on-surface));
  font-family: var(--mat-slide-toggle-label-text-font, var(--mat-sys-body-medium-font));
  line-height: var(--mat-slide-toggle-label-text-line-height, var(--mat-sys-body-medium-line-height));
  font-size: var(--mat-slide-toggle-label-text-size, var(--mat-sys-body-medium-size));
  letter-spacing: var(--mat-slide-toggle-label-text-tracking, var(--mat-sys-body-medium-tracking));
  font-weight: var(--mat-slide-toggle-label-text-weight, var(--mat-sys-body-medium-weight));
}
.mat-mdc-slide-toggle .mat-ripple-element {
  opacity: 0.12;
}
.mat-mdc-slide-toggle .mat-focus-indicator::before {
  border-radius: 50%;
}
.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle-track,
.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__icon,
.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle::before,
.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle::after,
.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__track::before,
.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__track::after {
  transition: none;
}
.mat-mdc-slide-toggle .mdc-switch:enabled + .mdc-label {
  cursor: pointer;
}
.mat-mdc-slide-toggle .mdc-switch--disabled + label {
  color: var(--mat-slide-toggle-disabled-label-text-color, var(--mat-sys-on-surface));
}
.mat-mdc-slide-toggle label:empty {
  display: none;
}

.mat-mdc-slide-toggle-touch-target {
  position: absolute;
  top: 50%;
  left: 50%;
  height: var(--mat-slide-toggle-touch-target-size, 48px);
  width: 100%;
  transform: translate(-50%, -50%);
  display: var(--mat-slide-toggle-touch-target-display, block);
}
[dir=rtl] .mat-mdc-slide-toggle-touch-target {
  left: auto;
  right: 50%;
  transform: translate(50%, -50%);
}
`],encapsulation:2})}return i})(),Ne=(()=>{class i{static \u0275fac=function(a){return new(a||i)};static \u0275mod=tE({type:i});static \u0275inj=Hl({imports:[L,Un]})}return i})();var I=class i{streamLog(n){return new M(e=>{let a=new EventSource(`/api/runs/${n}/log-stream`);return a.onmessage=t=>{e.next(t.data);},a.addEventListener("done",()=>{e.complete(),a.close();}),a.onerror=t=>{e.error(t),a.close();},()=>a.close()})}static \u0275fac=function(e){return new(e||i)};static \u0275prov=te({token:i,factory:i.\u0275fac,providedIn:"root"})};var Ue=(i,n)=>n.runStepId;function Xe(i,n){i&1&&(fi(0,"div",0),vD(1,"Loading run details..."),Sc());}function He(i,n){if(i&1){let e=PE();fi(0,"button",9),Op("click",function(){du(e);let t=$E(2);return fu(t.cancelRun())}),fi(1,"mat-icon"),vD(2,"stop"),Sc(),vD(3," Cancel Run "),Sc();}}function $e(i,n){if(i&1){let e=PE();fi(0,"div",7)(1,"mat-icon",10),vD(2),Sc(),fi(3,"div",11)(4,"div",12),vD(5),Sc(),fi(6,"div",13)(7,"span",14),vD(8),Sc(),fi(9,"span",15),vD(10),xD(11,"duration"),Sc(),Np(12,"app-status-badge",3),Sc()(),fi(13,"button",16),Op("click",function(){let t=du(e).$implicit,d=$E(2);return fu(d.openLog(t.runStepId))}),fi(14,"mat-icon"),vD(15,"visibility"),Sc(),vD(16," View Log "),Sc()();}if(i&2){let e=n.$implicit,a=$E(2);Tv(),qp("success",e.status==="SUCCESS")("failed",e.status==="FAILED"),Tv(),Oc(" ",a.statusIcon(e.status)," "),Tv(3),Kp(e.stepName),Tv(3),Kp(e.stepType),Tv(2),Kp(RD(11,9,e.durationSeconds)),Tv(2),Mp("status",e.status);}}function Qe(i,n){i&1&&Np(0,"mat-progress-bar",20);}function We(i,n){if(i&1&&(fi(0,"div",22),vD(1),Sc()),i&2){let e=n.$implicit;Tv(),Kp(e);}}function Je(i,n){if(i&1){let e=PE();fi(0,"div",8)(1,"div",17)(2,"h3"),vD(3,"Log Output"),Sc(),fi(4,"div",18)(5,"mat-slide-toggle",19),th$1("ngModelChange",function(t){du(e);let d=$E(2);return DD(d.autoScroll,t)||(d.autoScroll=t),fu(t)}),vD(6,"Auto-scroll"),Sc(),uI(),CE(7,Qe,1,0,"mat-progress-bar",20),Sc()(),fi(8,"div",21),NE(9,We,2,1,"div",22,_E),Sc()();}if(i&2){let e=$E(2);Tv(5),eh("ngModel",e.autoScroll),fI(),Tv(2),bE(!e.logComplete&&e.run.status==="RUNNING"?7:-1),Tv(2),SE(e.logLines);}}function Ze(i,n){if(i&1){let e=PE();fi(0,"div",1)(1,"button",2),Op("click",function(){du(e);let t=$E();return fu(t.goBack())}),fi(2,"mat-icon"),vD(3,"arrow_back"),Sc()(),fi(4,"h2"),vD(5),Sc(),Np(6,"app-status-badge",3),fi(7,"span",4),vD(8),xD(9,"duration"),Sc()(),CE(10,He,4,0,"button",5),fi(11,"h3"),vD(12,"Step Timeline"),Sc(),fi(13,"div",6),NE(14,$e,17,11,"div",7,Ue),Sc(),CE(16,Je,11,2,"div",8);}if(i&2){let e=$E();Tv(5),Jp("Run #",e.run.runId," \xB7 ",e.run.jobName),Tv(),Mp("status",e.run.status),Tv(2),Kp(RD(9,6,e.run.durationSeconds)),Tv(2),bE(e.run.status==="RUNNING"?10:-1),Tv(4),SE(e.run.steps),Tv(2),bE(e.logLines.length>0?16:-1);}}var Be=class i{runService=T(S$1);logStreamService=T(I);route=T(G);runId=null;run=null;loading=true;logLines=[];selectedStepId=null;autoScroll=true;logComplete=false;logSub;ngOnInit(){let n=this.route.snapshot.paramMap.get("runId");n&&(this.runId=Number(n),this.loadRun());}ngOnDestroy(){this.logSub?.unsubscribe();}loadRun(){this.runId!=null&&(this.loading=true,this.runService.getRunDetail(this.runId).subscribe({next:n=>{n.status==="SUCCESS"&&(this.run=n.data),this.loading=false;},error:()=>{this.loading=false;}}));}openLog(n){this.runId!=null&&(this.logSub?.unsubscribe(),this.logLines=[],this.selectedStepId=n,this.logComplete=false,this.run?.status==="RUNNING"?this.logSub=this.logStreamService.streamLog(this.runId).subscribe({next:e=>{this.logLines.push(e),this.autoScroll&&this.scrollToBottom();},complete:()=>{this.logComplete=true,this.logLines.push("--- Run complete ---");},error:()=>{this.logComplete=true,this.logLines.push("--- Stream disconnected ---");}}):this.runService.getStepLog(this.runId,n).subscribe({next:e=>{e.status==="SUCCESS"&&e.data&&(this.logLines=e.data.split(`
`).filter(Boolean)),this.logComplete=true;}}));}scrollToBottom(){setTimeout(()=>{let n=document.querySelector(".log-container");n&&(n.scrollTop=n.scrollHeight);},50);}cancelRun(){this.runId!=null&&this.runService.cancelRun(this.runId).subscribe({next:()=>this.loadRun()});}goBack(){window.location.hash="#/runs";}statusIcon(n){return {PENDING:"schedule",RUNNING:"pending",SUCCESS:"check_circle",FAILED:"error",PARTIAL:"warning",CANCELLED:"cancel",SKIPPED:"step_inplace"}[n]??"help"}static \u0275fac=function(e){return new(e||i)};static \u0275cmp=XI({type:i,selectors:[["app-run-detail"]],decls:2,vars:1,consts:[[1,"loading"],[1,"header-row"],["mat-icon-button","","matTooltip","Back",3,"click"],[3,"status"],[1,"duration"],["mat-flat-button","","color","warn"],[1,"step-timeline"],[1,"step-row"],[1,"log-section"],["mat-flat-button","","color","warn",3,"click"],[1,"step-icon"],[1,"step-info"],[1,"step-name"],[1,"step-meta"],[1,"step-type"],[1,"step-duration"],["mat-stroked-button","",3,"click"],[1,"log-header"],[1,"log-controls"],[3,"ngModelChange","ngModel"],["mode","indeterminate"],[1,"log-container"],[1,"log-line"]],template:function(e,a){e&1&&CE(0,Xe,2,0,"div",0)(1,Ze,17,8),e&2&&bE(a.loading?0:a.run?1:-1);},dependencies:[Xi,th,Xf,Tc,vg,bg,pu,Ci,Si,Z,L$1,Ne,L,Yt,mt,P,A],styles:[".header-row[_ngcontent-%COMP%]{display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap}.header-row[_ngcontent-%COMP%]   h2[_ngcontent-%COMP%]{margin:0}.duration[_ngcontent-%COMP%]{color:var(--mat-sys-on-surface-variant)}.step-timeline[_ngcontent-%COMP%]{display:flex;flex-direction:column;gap:8px;margin-top:16px}.step-row[_ngcontent-%COMP%]{display:flex;align-items:center;gap:12px;padding:12px;border-radius:8px;background:var(--mat-sys-surface-container-low)}.step-icon[_ngcontent-%COMP%]{flex-shrink:0}.step-icon.success[_ngcontent-%COMP%]{color:#4caf50}.step-icon.failed[_ngcontent-%COMP%]{color:#f44336}.step-info[_ngcontent-%COMP%]{flex:1;min-width:0}.step-name[_ngcontent-%COMP%]{font-weight:500}.step-meta[_ngcontent-%COMP%]{display:flex;align-items:center;gap:12px;font-size:.8rem;color:var(--mat-sys-on-surface-variant);margin-top:4px}.step-type[_ngcontent-%COMP%]{text-transform:uppercase;font-size:.7rem;letter-spacing:.5px}.log-section[_ngcontent-%COMP%]{margin-top:24px}.log-header[_ngcontent-%COMP%]{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}.log-controls[_ngcontent-%COMP%]{display:flex;align-items:center;gap:16px}.log-container[_ngcontent-%COMP%]{background:#1e1e1e;color:#d4d4d4;font-family:Consolas,Monaco,monospace;font-size:.8rem;padding:16px;border-radius:8px;max-height:500px;overflow-y:auto;line-height:1.6;white-space:pre-wrap}.log-line[_ngcontent-%COMP%]{white-space:pre}.loading[_ngcontent-%COMP%]{text-align:center;padding:48px;color:var(--mat-sys-on-surface-variant)}h3[_ngcontent-%COMP%]{margin:0}"]})};export{Be as RunDetailComponent};