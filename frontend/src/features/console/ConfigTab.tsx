'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { RotateCcw, Search, Plus, Trash2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Popover,
  PopoverClose,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { consoleApi } from './api'
import type { ConfigField, ConfigSchema } from './types'

const TIER_GROUP_PREFIX = 'LLM · Tier: '

/** Extracts the tier name from a group label like "LLM · Tier: cheap", or null. */
function tierNameFromGroup(group: string): string | null {
  return group.startsWith(TIER_GROUP_PREFIX) ? group.slice(TIER_GROUP_PREFIX.length) : null
}

/** Edit-form representation of a field's value (all stored as strings, incl. BOOL as 'true'/'false'). */
function toDraft(field: ConfigField): string {
  const v = field.value
  if (field.type === 'STRING_LIST') return Array.isArray(v) ? v.join(', ') : ''
  if (v === null || v === undefined) return ''
  return String(v)
}

/** Converts an edit-form draft back to the JSON value the backend expects. */
function toJsonValue(field: ConfigField, draft: string): unknown {
  switch (field.type) {
    case 'BOOL':
      return draft === 'true'
    case 'INT':
    case 'LONG':
      return Math.trunc(Number(draft))
    case 'DOUBLE':
      return Number(draft)
    case 'STRING_LIST':
      return draft
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
    default:
      return draft
  }
}

function formatValue(field: ConfigField, raw: unknown): string {
  if (field.type === 'STRING_LIST') return Array.isArray(raw) && raw.length ? raw.join(', ') : '—'
  if (raw === null || raw === undefined || raw === '') return '—'
  return String(raw)
}

const STACKED_TYPES = new Set(['STRING', 'STRING_LIST', 'TEXT'])

export function ConfigTab({
  canEdit,
  onError,
}: {
  canEdit: boolean
  onError: (message: string) => void
}) {
  const [schema, setSchema] = useState<ConfigSchema | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [resettingKey, setResettingKey] = useState<string | null>(null)
  const [deletingTier, setDeletingTier] = useState<string | null>(null)
  const [activeGroup, setActiveGroup] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  const draftsFromSchema = (next: ConfigSchema) =>
    Object.fromEntries(next.fields.map((f) => [f.key, toDraft(f)]))

  const applySchema = useCallback((next: ConfigSchema) => {
    setSchema(next)
    setDrafts(draftsFromSchema(next))
  }, [])

  const load = useCallback(async () => {
    try {
      applySchema(await consoleApi.getConfig())
    } catch {
      onError('Failed to load configuration.')
    }
  }, [applySchema, onError])

  useEffect(() => {
    void load()
  }, [load])

  const groups = useMemo(() => {
    if (!schema) return []
    const order: string[] = []
    const byGroup = new Map<string, ConfigField[]>()
    for (const field of schema.fields) {
      if (!byGroup.has(field.group)) {
        byGroup.set(field.group, [])
        order.push(field.group)
      }
      byGroup.get(field.group)!.push(field)
    }
    return order.map((name) => ({ name, fields: byGroup.get(name)! }))
  }, [schema])

  useEffect(() => {
    if (!activeGroup && groups.length) setActiveGroup(groups[0]!.name)
  }, [groups, activeGroup])

  const dirtyKeys = useMemo(() => {
    if (!schema) return []
    return schema.fields.filter((f) => (drafts[f.key] ?? '') !== toDraft(f)).map((f) => f.key)
  }, [schema, drafts])

  const q = query.trim().toLowerCase()
  const visibleGroups = useMemo(() => {
    if (q) {
      return groups
        .map((g) => ({
          ...g,
          fields: g.fields.filter(
            (f) => f.key.toLowerCase().includes(q) || f.label.toLowerCase().includes(q),
          ),
        }))
        .filter((g) => g.fields.length > 0)
    }
    const active = groups.find((g) => g.name === activeGroup)
    return active ? [active] : []
  }, [groups, q, activeGroup])

  const save = async () => {
    if (!schema || dirtyKeys.length === 0) return
    const byKey = new Map(schema.fields.map((f) => [f.key, f]))
    const values: Record<string, unknown> = {}
    for (const key of dirtyKeys) values[key] = toJsonValue(byKey.get(key)!, drafts[key] ?? '')
    setSaving(true)
    try {
      const next = await consoleApi.updateConfig(values)
      if (next) applySchema(next)
    } catch (e) {
      onError(
        e instanceof consoleApi.ApiError && e.status === 400
          ? 'Some values were rejected. Check ranges and allowed options.'
          : 'Failed to save configuration.',
      )
    } finally {
      setSaving(false)
    }
  }

  const resetField = async (key: string) => {
    setResettingKey(key)
    try {
      const next = await consoleApi.resetConfig(key)
      if (next) applySchema(next)
    } catch {
      onError('Failed to reset field.')
    } finally {
      setResettingKey(null)
    }
  }

  const discard = () => schema && setDrafts(draftsFromSchema(schema))

  const customTierNames = useMemo(
    () => new Set((schema?.tiers ?? []).filter((t) => t.custom).map((t) => t.name)),
    [schema],
  )

  const addTier = async (body: {
    name: string
    model: string
    temperature: number
    maxTokens: number
  }) => {
    const next = await consoleApi.addTier(body)
    if (next) {
      applySchema(next)
      setActiveGroup(`${TIER_GROUP_PREFIX}${body.name}`)
    }
  }

  const deleteTier = async (name: string) => {
    setDeletingTier(name)
    try {
      const next = await consoleApi.deleteTier(name)
      if (next) {
        applySchema(next)
        setActiveGroup(null)
      }
    } catch (e) {
      onError(
        e instanceof consoleApi.ApiError && (e.status === 400 || e.status === 409)
          ? e.message || 'This tier cannot be deleted.'
          : 'Failed to delete tier.',
      )
    } finally {
      setDeletingTier(null)
    }
  }

  if (!schema) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-9 w-full" />
        <div className="flex gap-3">
          <Skeleton className="hidden h-64 w-48 sm:block" />
          <Skeleton className="h-64 flex-1" />
        </div>
      </div>
    )
  }

  const overriddenByGroup = new Map(groups.map((g) => [g.name, g.fields.some((f) => f.overridden)]))

  return (
    <div className="flex flex-col gap-3">
      {/* Sticky toolbar: search + live-apply hint + save/discard */}
      <div className="sticky top-0 z-10 -mx-1 flex flex-wrap items-center gap-2 rounded-lg border bg-background/95 px-2 py-2 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="relative flex-1 basis-56">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search settings…"
            className="h-8 pl-8"
          />
        </div>
        <span className="hidden text-xs text-muted-foreground md:inline">
          Applies live · no restart
        </span>
        {canEdit && (
          <div className="flex items-center gap-2">
            {dirtyKeys.length > 0 && (
              <Badge variant="outline" className="font-normal text-amber-600">
                {dirtyKeys.length} unsaved
              </Badge>
            )}
            <Button
              size="sm"
              variant="ghost"
              className="h-8"
              disabled={saving || dirtyKeys.length === 0}
              onClick={discard}
            >
              Discard
            </Button>
            <Button
              size="sm"
              className="h-8"
              disabled={saving || dirtyKeys.length === 0}
              onClick={save}
            >
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        {/* Group navigation (hidden while searching) */}
        {!q && (
          <nav className="flex gap-1 overflow-x-auto pb-1 sm:sticky sm:top-14 sm:w-48 sm:shrink-0 sm:flex-col sm:overflow-visible sm:pb-0">
            {groups.map((g) => (
              <button
                key={g.name}
                type="button"
                onClick={() => setActiveGroup(g.name)}
                className={cn(
                  'flex shrink-0 items-center justify-between gap-2 rounded-md px-2.5 py-1.5 text-left text-sm transition-colors sm:shrink',
                  g.name === activeGroup
                    ? 'bg-accent font-medium text-accent-foreground'
                    : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground',
                )}
              >
                <span className="whitespace-nowrap">{g.name}</span>
                <span className="flex items-center gap-1.5">
                  {overriddenByGroup.get(g.name) && (
                    <span
                      className="size-1.5 rounded-full bg-amber-500"
                      aria-label="has overrides"
                    />
                  )}
                  <span className="text-xs tabular-nums text-muted-foreground/70">
                    {g.fields.length}
                  </span>
                </span>
              </button>
            ))}
            {canEdit && <AddTierForm onAdd={addTier} />}
          </nav>
        )}

        {/* Fields */}
        <div className="min-w-0 flex-1">
          {visibleGroups.length === 0 ? (
            <div className="rounded-lg border border-dashed py-10 text-center text-sm text-muted-foreground">
              No settings match “{query}”.
            </div>
          ) : (
            <div className="flex flex-col gap-4">
              {visibleGroups.map((group) => (
                <section key={group.name} className="rounded-lg border">
                  {q && (
                    <div className="border-b bg-muted/40 px-3 py-1.5 text-xs font-medium text-muted-foreground">
                      {group.name}
                    </div>
                  )}
                  {!q && tierNameFromGroup(group.name) && customTierNames.has(tierNameFromGroup(group.name)!) && canEdit && (
                    <div className="flex items-center justify-between border-b bg-muted/40 px-3 py-1.5">
                      <span className="text-xs font-medium text-muted-foreground">
                        Custom tier
                      </span>
                      <DeleteTierButton
                        name={tierNameFromGroup(group.name)!}
                        deleting={deletingTier === tierNameFromGroup(group.name)}
                        onDelete={() => deleteTier(tierNameFromGroup(group.name)!)}
                      />
                    </div>
                  )}
                  <div className="divide-y">
                    {group.fields.map((field) => (
                      <FieldRow
                        key={field.key}
                        field={field}
                        value={drafts[field.key] ?? ''}
                        dirty={(drafts[field.key] ?? '') !== toDraft(field)}
                        canEdit={canEdit}
                        saving={saving}
                        resetting={resettingKey === field.key}
                        onChange={(v) => setDrafts((prev) => ({ ...prev, [field.key]: v }))}
                        onReset={() => resetField(field.key)}
                      />
                    ))}
                  </div>
                </section>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function ResetButton({
  field,
  resetting,
  onReset,
}: {
  field: ConfigField
  resetting: boolean
  onReset: () => void
}) {
  const [open, setOpen] = useState(false)
  const defaultText = formatValue(field, field.defaultValue)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="size-7 shrink-0 text-muted-foreground hover:text-amber-600"
            disabled={resetting}
            title="Reset to default"
            aria-label={`Reset ${field.label} to default`}
          >
            <RotateCcw className={cn('size-3.5', resetting && 'animate-spin')} />
          </Button>
        }
      />
      <PopoverContent side="top" align="end" className="w-64">
        <div className="flex flex-col gap-2.5">
          <div className="space-y-1">
            <p className="text-sm font-medium">Reset to default?</p>
            <p className="text-xs leading-relaxed text-muted-foreground">
              <span className="font-medium text-foreground">{field.label}</span> will revert to{' '}
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-[11px] text-foreground">
                {defaultText}
              </code>{' '}
              and its override will be removed.
            </p>
          </div>
          <div className="flex justify-end gap-2">
            <PopoverClose
              render={
                <Button type="button" size="sm" variant="ghost" className="h-7">
                  Cancel
                </Button>
              }
            />
            <Button
              type="button"
              size="sm"
              className="h-7"
              onClick={() => {
                onReset()
                setOpen(false)
              }}
            >
              Reset
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function DeleteTierButton({
  name,
  deleting,
  onDelete,
}: {
  name: string
  deleting: boolean
  onDelete: () => void
}) {
  const [open, setOpen] = useState(false)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="size-6 text-muted-foreground hover:text-destructive"
            disabled={deleting}
            title="Delete tier"
            aria-label={`Delete tier ${name}`}
          >
            <Trash2 className={cn('size-3.5', deleting && 'animate-pulse')} />
          </Button>
        }
      />
      <PopoverContent side="bottom" align="end" className="w-64">
        <div className="flex flex-col gap-2.5">
          <div className="space-y-1">
            <p className="text-sm font-medium">Delete tier?</p>
            <p className="text-xs leading-relaxed text-muted-foreground">
              The tier{' '}
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-[11px] text-foreground">
                {name}
              </code>{' '}
              and its overrides will be permanently removed. Tiers currently referenced by a role
              cannot be deleted.
            </p>
          </div>
          <div className="flex justify-end gap-2">
            <PopoverClose
              render={
                <Button type="button" size="sm" variant="ghost" className="h-7">
                  Cancel
                </Button>
              }
            />
            <Button
              type="button"
              size="sm"
              variant="destructive"
              className="h-7"
              onClick={() => {
                onDelete()
                setOpen(false)
              }}
            >
              Delete
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function AddTierForm({
  onAdd,
}: {
  onAdd: (body: {
    name: string
    model: string
    temperature: number
    maxTokens: number
  }) => Promise<void>
}) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [model, setModel] = useState('')
  const [temperature, setTemperature] = useState('0.2')
  const [maxTokens, setMaxTokens] = useState('500')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reset = () => {
    setName('')
    setModel('')
    setTemperature('0.2')
    setMaxTokens('500')
    setError(null)
  }

  const submit = async () => {
    setError(null)
    const trimmedName = name.trim()
    const trimmedModel = model.trim()
    if (!/^[a-z][a-z0-9-]{1,49}$/.test(trimmedName)) {
      setError('Name must be lowercase letters, digits or hyphens (2–50 chars, starting with a letter).')
      return
    }
    if (!trimmedModel) {
      setError('Model is required.')
      return
    }
    const temp = Number(temperature)
    const tokens = Math.trunc(Number(maxTokens))
    if (Number.isNaN(temp) || temp < 0 || temp > 2) {
      setError('Temperature must be between 0 and 2.')
      return
    }
    if (Number.isNaN(tokens) || tokens < 1 || tokens > 8000) {
      setError('Max tokens must be between 1 and 8000.')
      return
    }
    setSubmitting(true)
    try {
      await onAdd({ name: trimmedName, model: trimmedModel, temperature: temp, maxTokens: tokens })
      reset()
      setOpen(false)
    } catch (e) {
      setError(
        e instanceof consoleApi.ApiError ? e.message : 'Failed to add tier. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) reset()
      }}
    >
      <PopoverTrigger
        render={
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="mt-1 h-8 justify-start gap-1.5 border-dashed text-muted-foreground"
          >
            <Plus className="size-4" />
            Add tier
          </Button>
        }
      />
      <PopoverContent side="right" align="start" className="w-72">
        <div className="flex flex-col gap-3">
          <p className="text-sm font-medium">New tier</p>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground" htmlFor="new-tier-name">
              Name
            </label>
            <Input
              id="new-tier-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. reasoning"
              className="h-8"
              autoComplete="off"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground" htmlFor="new-tier-model">
              Model
            </label>
            <Input
              id="new-tier-model"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="e.g. openai/gpt-4o-mini"
              className="h-8 font-mono text-xs"
              autoComplete="off"
            />
            <span className="text-[11px] text-muted-foreground/70">
              Verified against OpenRouter on save.
            </span>
          </div>
          <div className="flex gap-2">
            <div className="flex flex-1 flex-col gap-1">
              <label className="text-xs font-medium text-muted-foreground" htmlFor="new-tier-temp">
                Temperature
              </label>
              <Input
                id="new-tier-temp"
                type="number"
                inputMode="decimal"
                step="any"
                min={0}
                max={2}
                value={temperature}
                onChange={(e) => setTemperature(e.target.value)}
                className="h-8 tabular-nums"
              />
            </div>
            <div className="flex flex-1 flex-col gap-1">
              <label
                className="text-xs font-medium text-muted-foreground"
                htmlFor="new-tier-tokens"
              >
                Max tokens
              </label>
              <Input
                id="new-tier-tokens"
                type="number"
                inputMode="numeric"
                step={1}
                min={1}
                max={8000}
                value={maxTokens}
                onChange={(e) => setMaxTokens(e.target.value)}
                className="h-8 tabular-nums"
              />
            </div>
          </div>
          {error && <p className="text-xs leading-relaxed text-destructive">{error}</p>}
          <div className="flex justify-end gap-2">
            <PopoverClose
              render={
                <Button type="button" size="sm" variant="ghost" className="h-7">
                  Cancel
                </Button>
              }
            />
            <Button type="button" size="sm" className="h-7" disabled={submitting} onClick={submit}>
              {submitting ? 'Verifying…' : 'Add tier'}
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

function FieldRow({
  field,
  value,
  dirty,
  canEdit,
  saving,
  resetting,
  onChange,
  onReset,
}: {
  field: ConfigField
  value: string
  dirty: boolean
  canEdit: boolean
  saving: boolean
  resetting: boolean
  onChange: (value: string) => void
  onReset: () => void
}) {
  const disabled = !canEdit || saving
  const stacked = STACKED_TYPES.has(field.type)

  const meta = (
    <div className="min-w-0">
      <div className="flex items-center gap-1.5">
        <span className="truncate text-sm font-medium">{field.label}</span>
        {dirty && (
          <span
            className="size-1.5 shrink-0 rounded-full bg-amber-500"
            title="Unsaved change"
          />
        )}
        {field.overridden && (
          <Badge
            variant="secondary"
            className="h-4 shrink-0 border-amber-500/30 bg-amber-500/10 px-1.5 text-[10px] font-normal text-amber-600 dark:text-amber-400"
            title={`Overrides app default: ${formatValue(field, field.defaultValue)}`}
          >
            Modified
          </Badge>
        )}
      </div>
      <div className="truncate font-mono text-[11px] leading-tight text-muted-foreground/70">
        {field.key}
      </div>
    </div>
  )

  const resetBtn = canEdit && field.overridden && (
    <ResetButton field={field} resetting={resetting} onReset={onReset} />
  )

  if (stacked) {
    return (
      <div
        className={cn(
          'flex flex-col gap-1.5 px-3 py-2.5',
          field.overridden && 'border-l-2 border-l-amber-500/60',
        )}
      >
        <div className="flex items-center justify-between gap-2">
          {meta}
          {resetBtn}
        </div>
        <FieldControl field={field} value={value} disabled={disabled} onChange={onChange} stacked />
      </div>
    )
  }

  return (
    <div
      className={cn(
        'flex items-center gap-3 px-3 py-2',
        field.overridden && 'border-l-2 border-l-amber-500/60',
      )}
    >
      <div className="flex-1">{meta}</div>
      {resetBtn}
      <FieldControl field={field} value={value} disabled={disabled} onChange={onChange} />
    </div>
  )
}

function FieldControl({
  field,
  value,
  disabled,
  onChange,
  stacked = false,
}: {
  field: ConfigField
  value: string
  disabled: boolean
  onChange: (value: string) => void
  stacked?: boolean
}) {
  if (field.type === 'BOOL') {
    return (
      <Switch
        checked={value === 'true'}
        disabled={disabled}
        onCheckedChange={(checked) => onChange(checked ? 'true' : 'false')}
      />
    )
  }

  if (field.type === 'ENUM') {
    return (
      <Select value={value} onValueChange={(v) => onChange(v ?? '')} disabled={disabled}>
        <SelectTrigger size="sm" className="w-44">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {field.enumValues.map((opt) => (
            <SelectItem key={opt} value={opt}>
              {opt}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    )
  }

  if (field.type === 'TEXT') {
    return (
      <Textarea
        id={field.key}
        value={value}
        readOnly={disabled}
        className="max-h-80 min-h-28 resize-y font-mono text-xs"
        onChange={(e) => onChange(e.target.value)}
      />
    )
  }

  const isNumber = field.type === 'INT' || field.type === 'LONG' || field.type === 'DOUBLE'
  const hasRange = isNumber && (field.min !== null || field.max !== null)

  return (
    <div className={cn('flex items-center gap-2', stacked ? 'w-full' : 'justify-end')}>
      <Input
        id={field.key}
        type={isNumber ? 'number' : 'text'}
        inputMode={field.type === 'DOUBLE' ? 'decimal' : isNumber ? 'numeric' : undefined}
        step={field.type === 'DOUBLE' ? 'any' : isNumber ? 1 : undefined}
        min={isNumber && field.min !== null ? field.min : undefined}
        max={isNumber && field.max !== null ? field.max : undefined}
        value={value}
        readOnly={disabled}
        placeholder={field.type === 'STRING_LIST' ? 'comma, separated, values' : undefined}
        className={cn(
          'h-8',
          isNumber ? 'w-24 text-right tabular-nums' : stacked ? 'w-full font-mono text-xs' : 'w-56',
        )}
        onChange={(e) => onChange(e.target.value)}
      />
      {hasRange && !stacked && (
        <span className="w-16 shrink-0 text-right text-[11px] tabular-nums text-muted-foreground/70">
          {field.min ?? '−∞'}–{field.max ?? '∞'}
        </span>
      )}
    </div>
  )
}
