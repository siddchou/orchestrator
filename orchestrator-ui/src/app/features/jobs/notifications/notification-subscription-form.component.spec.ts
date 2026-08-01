import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NotificationSubscriptionFormComponent } from './notification-subscription-form.component';
import { ChannelConfigSchema } from '@app/core/models/notification.model';

const EMAIL_SCHEMA: ChannelConfigSchema = {
  type: 'EMAIL',
  fields: [
    { name: 'toEmail', label: 'To Email', type: 'STRING', required: true },
  ],
};

describe('NotificationSubscriptionFormComponent', () => {
  let component: NotificationSubscriptionFormComponent;
  let fixture: ComponentFixture<NotificationSubscriptionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { jobId: 10, mode: 'create', channelSchemas: [EMAIL_SCHEMA] },
        },
        { provide: MatSnackBar, useValue: { open: vi.fn(), dismissAll: vi.fn() } },
      ],
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
});

describe('NotificationSubscriptionFormComponent - Edit Mode', () => {
  let component: NotificationSubscriptionFormComponent;
  let fixture: ComponentFixture<NotificationSubscriptionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
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
          },
        },
        { provide: MatSnackBar, useValue: { open: vi.fn(), dismissAll: vi.fn() } },
      ],
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
});

describe('NotificationSubscriptionFormComponent - No Channels', () => {
  let component: NotificationSubscriptionFormComponent;
  let fixture: ComponentFixture<NotificationSubscriptionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, NotificationSubscriptionFormComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { jobId: 10, mode: 'create', channelSchemas: [] },
        },
        { provide: MatSnackBar, useValue: { open: vi.fn(), dismissAll: vi.fn() } },
      ],
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
