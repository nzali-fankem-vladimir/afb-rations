import { forwardRef } from 'react'
import type { TextareaHTMLAttributes } from 'react'

import { cn } from '../../utils/cn'

export type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement>

// Meme discipline que Input (Sprint 7F.1) : pas de correction automatique sur un
// motif de retour ou de rejet, qui rejoint le journal d'audit tel quel.
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, spellCheck = false, rows = 3, ...props }, ref) => (
    <textarea
      spellCheck={spellCheck}
      rows={rows}
      className={cn(
        'flex w-full rounded border border-neutral-500 bg-white px-3 py-2 text-sm text-neutral-900 placeholder:text-neutral-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      ref={ref}
      {...props}
    />
  ),
)
Textarea.displayName = 'Textarea'
