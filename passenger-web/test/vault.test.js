import test from 'node:test'
import assert from 'node:assert/strict'
import {sealSession,openSession} from '../vault.js'
test('optional device storage contains ciphertext and requires the correct password',async()=>{
  const session={origin:'https://api.example',accountId:'account',accessToken:'private-token-test'},password='test-password-123'
  const encrypted=await sealSession(session,password)
  assert.ok(!JSON.stringify(encrypted).includes(session.accessToken))
  assert.deepEqual(await openSession(encrypted,password),session)
  await assert.rejects(openSession(encrypted,'incorrect-password'))
  const other=await sealSession(session,password)
  assert.notEqual(encrypted.iv,other.iv);assert.notEqual(encrypted.ciphertext,other.ciphertext)
})
test('rejects weak passwords and ciphertext tampering',async()=>{
  await assert.rejects(sealSession({accessToken:'test'},'1234'))
  const encrypted=await sealSession({origin:'https://api.example',accountId:'test',accessToken:'test'},'long-password')
  encrypted.ciphertext='AAAA'+encrypted.ciphertext.slice(4)
  await assert.rejects(openSession(encrypted,'long-password'))
})
