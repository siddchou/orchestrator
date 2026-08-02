import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { NotificationSubscriptionFormComponent } from './notification-subscription-form.component';
import { ChannelConfigSchema } from '@app/core/models/notification.model';
import { NotificationService } from '@app/core/services/notification.service';
import { CredentialService } from '@app/core/services/credential.service';

const EMAIL_SCHEMA: ChannelConfigSchema = {
  type: 'EMAIL',
  fields: [
    { name: 'toEmail', label: 'To Email', type: 'STRING', required: true },
  ],
};

function dialogMock(): any {
  return {
    open: vi.fn().mockReturnValue({ afterClosed: () => ({ subscribe: vi.fn() }) }),
  };
}

function defaultProviders(data: any) {
  return [
    { provide: MatDialogRef, useValue: { close: vi.fn() } },
    { provide: MAT_DIALOG_DATA, useValue: data },
    { provide: MatSnackBar, useValue: { open: vi.fn(), dismissAll: vi.fn() } },
    { provide: MatDialog, useValue: dialogMock() },
    {
      provide: NotificationService,
      useValue: {
        createSubscription: () => of({ status: 'SUCCESS', data: {} }),
        updateSubscription: () => of({ status: 'SUCCESS', data: {} }),
      },
    },
    {
      provide: CredentialService,
      useValue: { listCredentials: () => of({ status: 'SUCCESS', data: [] }) },
    },
  ];
}

describe('NotificationSubscriptionFormComponent', () => {
  let component: NotificationSubscriptionFormComponent;
  let fixture: ComponentFixture<NotificationSubscriptionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: defaultProviders({ jobId: 10, mode: 'create', channelSchemas: [EMAIL_SCHEMA] }),
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationSubscriptionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads channel schemas into dropdown', () => {
    expect(component.channelSchemas.length).toBe(1);
    expect(component.form.get('channelType')).toBeTruthy();
  });

  it('selects first schema by default', () => {
    expect(component.form.get('channelType')?.value).toBe('EMAIL');
  });

  it('requires a channel type to be selected', () => {
    component.form.setValue({ channelType: '' });
    expect(component.form.get('channelType')?.valid).toBe(false);
  });

  describe('typeToLabel', () => {
    it('returns readable labels for known types', () => {
      expect(component.typeToLabel('EMAIL')).toBe('Email');
      expect(component.typeToLabel('SLACK_WEBHOOK')).toBe('Slack Webhook');
      expect(component.typeToLabel('GENERIC_WEBHOOK')).toBe('Generic Webhook');
    });

    it('returns the type itself for unknown types', () => {
      expect(component.typeToLabel('UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE');
    });
  });

  describe('event checkbox change', () => {
    it('adds event to selected set when checked', () => {
      component.onEventCheckboxChange('PARTIAL', true);
      expect(component.selectedEvents.has('PARTIAL')).toBe(true);
    });

    it('removes event from selected set when unchecked', () => {
      component.onEventCheckboxChange('SUCCESS', false);
      expect(component.selectedEvents.has('SUCCESS')).toBe(false);
    });
  });

  describe('saving state (Fix 2)', () => {
    it('starts with saving=false', () => {
      expect(component.saving).toBe(false);
    });
  });

  describe('header icon (Fix 4)', () => {
    it('updates headerIcon to match channel type after init', () => {
      // After patching EMAIL, headerIcon should be 'mail'
      expect(component.headerIcon).toBe('mail');
    });

    it('maps icons correctly for all known types', () => {
      expect(component.typeToIcon('EMAIL')).toBe('mail');
      expect(component.typeToIcon('SLACK_WEBHOOK')).toBe('chat');
      expect(component.typeToIcon('GENERIC_WEBHOOK')).toBe('link');
    });

    it('falls back to notifications_active for unknown type', () => {
      expect(component.typeToIcon('UNKNOWN')).toBe('notifications_active');
    });
  });

  describe('dirty check (Fix 5)', () => {
    it('is not dirty on initial load', () => {
      expect(component.isDirty()).toBe(false);
    });

    it('is dirty after form change', () => {
      component.form.markAsDirty();
      expect(component.isDirty()).toBe(true);
    });

    it('is dirty after toggling events', () => {
      component.toggleEvent('PARTIAL');
      expect(component.isDirty()).toBe(true);
    });

    it('is not dirty after toggling event then toggling back', () => {
      component.toggleEvent('PARTIAL');
      component.toggleEvent('PARTIAL');
      expect(component.isDirty()).toBe(false);
    });
  });

  describe('cancel with dirty check (Fix 5)', () => {
    it('opens confirm dialog when form is dirty', () => {
      const mockDialog = {
        open: vi.fn().mockReturnValue({ afterClosed: () => ({ subscribe: vi.fn() }) }),
      };
      (component as any).dialog = mockDialog;

      component.form.markAsDirty();
      component.cancel();

      expect(mockDialog.open).toHaveBeenCalled();
    });

    it('does not open confirm dialog when form is clean', () => {
      const dialogRefCloseSpy = vi.fn();
      const mockDialog = {
        open: vi.fn().mockReturnValue({ afterClosed: () => ({ subscribe: vi.fn() }) }),
      };
      (component as any).dialogRef.close = dialogRefCloseSpy;
      (component as any).dialog = mockDialog;

      component.cancel();

      expect(mockDialog.open).not.toHaveBeenCalled();
      expect(dialogRefCloseSpy).toHaveBeenCalled();
    });
  });
});

describe('NotificationSubscriptionFormComponent - Edit Mode', () => {
  let component: NotificationSubscriptionFormComponent;
  let fixture: ComponentFixture<NotificationSubscriptionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: defaultProviders({
        jobId: 10,
        mode: 'edit',
        subscription: {
          id: 1,
          jobId: 10,
          channelType: 'EMAIL',
          events: 'SUCCESS,FAILED',
          config: {},
          active: true,
          createdAt: new Date().toISOString(),
        },
        channelSchemas: [EMAIL_SCHEMA],
      }),
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationSubscriptionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('populates form from subscription data', () => {
    expect(component.form.get('channelType')?.value).toBe('EMAIL');
  });

  it('parses comma-separated events into set', () => {
    expect(component.selectedEvents.has('SUCCESS')).toBe(true);
    expect(component.selectedEvents.has('FAILED')).toBe(true);
  });

  it('sets header icon for edit mode subscription channel', () => {
    expect(component.headerIcon).toBe('mail');
  });
});

describe('NotificationSubscriptionFormComponent - No Channels', () => {
  let component: NotificationSubscriptionFormComponent;
  let fixture: ComponentFixture<NotificationSubscriptionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: defaultProviders({ jobId: 10, mode: 'create', channelSchemas: [] }),
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationSubscriptionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('has empty channel dropdown when schemas are empty', () => {
    expect(component.channelSchemas.length).toBe(0);
    expect(component.selectedChannelSchema).toBeNull();
  });
});

describe('NotificationSubscriptionFormComponent - Event accent colors (Fix 3)', () => {
  let component: NotificationSubscriptionFormComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: defaultProviders({ jobId: 10, mode: 'create', channelSchemas: [EMAIL_SCHEMA] }),
    }).compileComponents();

    component = TestBed.createComponent(NotificationSubscriptionFormComponent).componentInstance;
    component.ngOnInit();
  });

  it('returns green for SUCCESS', () => {
    expect(component.getEventAccentColor('SUCCESS')).toBe('#16a34a');
  });

  it('returns red for FAILED', () => {
    expect(component.getEventAccentColor('FAILED')).toBe('#dc2626');
  });

  it('returns orange for PARTIAL', () => {
    expect(component.getEventAccentColor('PARTIAL')).toBe('#ea580c');
  });

  it('returns gray for CANCELLED', () => {
    expect(component.getEventAccentColor('CANCELLED')).toBe('#6b7280');
  });
});
